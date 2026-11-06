package com.juanpablo.evermail.config;

import com.juanpablo.evermail.exception.DatabaseException;
import com.juanpablo.evermail.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Disk budget, not a memory limit. All sizes include a conservative allocation allowance. */
public final class StorageBudget {
    public static final long TOTAL_BYTES = 2_000_000_000L;
    public static final long OPERATING_RESERVE = 32L * 1024 * 1024;
    private final Path database;
    private final List<Path> applicationPaths;
    private final long limit;
    private final long reserve;
    private final FreeSpace freeSpace;
    private final boolean includeRuntimeCaches;

    @FunctionalInterface
    public interface FreeSpace { long available(Path directory) throws IOException; }

    public StorageBudget(Path database, List<Path> applicationPaths, long limit, long reserve,
                         FreeSpace freeSpace) {
        this(database, applicationPaths, limit, reserve, freeSpace, false);
    }

    private StorageBudget(Path database, List<Path> applicationPaths, long limit, long reserve,
                          FreeSpace freeSpace, boolean includeRuntimeCaches) {
        this.database = database.toAbsolutePath().normalize();
        this.applicationPaths = List.copyOf(applicationPaths);
        if (limit <= reserve || reserve < 0) throw new IllegalArgumentException("Invalid disk budget");
        this.limit = limit;
        this.reserve = reserve;
        this.freeSpace = Objects.requireNonNull(freeSpace);
        this.includeRuntimeCaches = includeRuntimeCaches;
    }

    /** Explicit paths also make tests independent of the user's installation and account data. */
    public static StorageBudget local(Path database) {
        return new StorageBudget(database, List.of(), TOTAL_BYTES, OPERATING_RESERVE,
                directory -> Files.getFileStore(directory).getUsableSpace());
    }

    public static StorageBudget application(Path database) {
        return new StorageBudget(database, applicationPaths(), TOTAL_BYTES, OPERATING_RESERVE,
                directory -> Files.getFileStore(directory).getUsableSpace(), true);
    }

    private static List<Path> applicationPaths() {
        String executable = System.getProperty("jpackage.app-path");
        if (executable != null && !executable.isBlank()) {
            return List.of(Path.of(executable).toAbsolutePath().getParent());
        }
        // Development/jlink: count the actual runtime, modules, classes and resources, not the repo.
        List<Path> paths = new ArrayList<>();
        paths.add(Path.of(System.getProperty("java.home")));
        for (String property : List.of("java.class.path", "jdk.module.path")) {
            for (String entry : System.getProperty(property, "").split(java.io.File.pathSeparator)) {
                if (!entry.isBlank()) paths.add(Path.of(entry));
            }
        }
        return paths;
    }

    /** Libraries may unpack DLLs outside the installation. Count their caches without deleting them. */
    private static List<Path> runtimeCaches() throws IOException {
        List<Path> paths = new ArrayList<>();
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        String user = System.getProperty("user.name", "anonymous");
        String fxVersion = System.getProperty("javafx.runtime.version", "versionless");
        String architecture = System.getProperty("os.arch");
        paths.add(Path.of(System.getProperty("javafx.cachedir",
                Path.of(System.getProperty("user.home"), ".openjfx", "cache", fxVersion, architecture).toString())));
        paths.add(temporary.resolve(".openjfx_" + user).resolve("cache").resolve(fxVersion).resolve(architecture));
        paths.add(Path.of(System.getProperty("jna.tmpdir", temporary.resolve("jna-" + user.hashCode()).toString())));
        String dispatch = System.getProperty("jnidispatch.path");
        if (dispatch != null) paths.add(Path.of(dispatch));
        Path sqliteTemporary = Path.of(System.getProperty("org.sqlite.tmpdir", temporary.toString()));
        if (Files.isDirectory(sqliteTemporary)) {
            try (var files = Files.newDirectoryStream(sqliteTemporary, "sqlite-*")) {
                for (Path file : files) paths.add(file);
            }
        }
        return paths;
    }

    public Path database() { return database; }

    /**
     * Reserve two additional database-sized copies for rollback/compaction, plus fixed headroom
     * for native libraries, credential-store records, allocation metadata and small auxiliary files.
     * Unrelated files in the data directory count against the budget but are never deleted.
     */
    public long databaseLimit() throws DatabaseException {
        return databaseLimit(3);
    }

    /** Deleting cached bodies needs a rollback journal, but no temporary VACUUM database. */
    public long cleanupLimit() throws DatabaseException {
        return databaseLimit(2);
    }

    private long databaseLimit(int copies) throws DatabaseException {
        try {
            long databaseBytes = allocated(database);
            List<Path> roots = new ArrayList<>(applicationPaths);
            if (includeRuntimeCaches) roots.addAll(runtimeCaches());
            roots.add(database.getParent());
            long occupied = measure(roots);
            long other = Math.max(0, occupied - databaseBytes);
            long totalAllowance = (limit - other - reserve) / copies;
            long diskAllowance = (freeSpace.available(database.getParent()) + databaseBytes - reserve) / copies;
            long allowed = Math.min(totalAllowance, diskAllowance);
            if (allowed < 4096 || occupied > limit) throw exhausted();
            return allowed;
        } catch (IOException | ArithmeticException error) {
            throw new DatabaseException(ErrorCode.STORAGE_LIMIT_REACHED, "Cannot measure storage safely", error);
        }
    }

    public void checkOpening() throws DatabaseException {
        try {
            Files.createDirectories(database.getParent());
            // Include a possible hot WAL in the space needed to recover an interrupted session.
            long recovered = allocated(database) + allocated(Path.of(database + "-wal"));
            if (recovered > cleanupLimit()) throw exhausted();
        } catch (IOException error) {
            throw new DatabaseException(ErrorCode.STORAGE_LIMIT_REACHED, "Cannot prepare storage", error);
        }
    }

    public static DatabaseException exhausted() {
        return new DatabaseException(ErrorCode.STORAGE_LIMIT_REACHED, "Local storage budget exhausted");
    }

    static long measure(List<Path> paths) throws IOException {
        List<Path> roots = new ArrayList<>();
        for (Path value : paths) {
            if (Files.notExists(value)) continue;
            Path path = value.toRealPath();
            if (roots.stream().anyMatch(path::startsWith)) continue;
            roots.removeIf(root -> root.startsWith(path));
            roots.add(path);
        }
        long[] bytes = {0};
        Set<Path> files = new HashSet<>();
        for (Path root : roots) {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                            if (files.add(file.toRealPath())) bytes[0] = Math.addExact(bytes[0], roundAllocation(attributes.size()));
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }
        return bytes[0];
    }

    private static long allocated(Path path) throws IOException {
        return Files.exists(path) ? roundAllocation(Files.size(path)) : 0;
    }

    private static long roundAllocation(long size) {
        return Math.multiplyExact(Math.addExact(size, 4095) / 4096, 4096);
    }
}
