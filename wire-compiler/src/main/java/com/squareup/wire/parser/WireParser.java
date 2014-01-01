package com.squareup.wire.parser;

import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.ProtoSchemaParser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.squareup.wire.parser.ProtoQualifier.fullyQualifyProtos;
import static com.squareup.wire.parser.ProtoUtils.collectAllTypes;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableSet;

/**
 * Intelligently parse {@code .proto} files into an object model which represents a set of types
 * and all of their transitive dependencies.
 * <p>
 * There are three methods which control parsing:
 * <ul>
 * <li>{@link #addDirectory(File) addDirectory} specifies a directory under which proto files
 * reside. Directories are used to resolve {@code include} declarations. If no directories are
 * specified, the current working directory will be used.</li>
 * <li>{@link #addProto(File) addProto} specifies which proto files to parse. If no proto files are
 * specified, all files under every directory will be used.</li>
 * <li>{@link #addTypeRoot(String) addTypeRoot} specifies which types to include. If no types are
 * specified, all types in every proto file will be used.</li>
 * </ul>
 * Given no data, an instance of this class will recursively find all files in the current working
 * directory, attempt to parse them as protocol buffer definitions, and verify that all of the
 * dependencies of the types contained within those definitions are met.
 * <p>
 * The API of this class is meant to mimic the builder pattern and should be used as such.
 */
public class WireParser {
  private final Set<File> directories = new LinkedHashSet<File>();
  private final Set<File> protos = new LinkedHashSet<File>();
  private final Set<String> types = new LinkedHashSet<String>();

  private final Filesystem fs;

  public WireParser() {
    this(Filesystem.DISK);
  }

  WireParser(Filesystem fs) {
    this.fs = fs;
  }

  /**
   * Add a directory under which proto files reside. {@code include} declarations will be resolved
   * from these directories.
   * <p>
   * If no directories are specified, the current working directory will be used.
   */
  public WireParser addDirectory(File directory) {
    Preconditions.notNull(directory, "Directory must not be null.");
    directories.add(directory);
    return this;
  }

  /**
   * Add a proto file to parse.
   * <p>
   * If no proto files are specified, every file in the specified directories will be used.
   * <p>
   * It is an error to call this method with a proto path that is not contained in one of the
   * specified directories.
   */
  public WireParser addProto(File proto) {
    Preconditions.notNull(proto, "Proto must not be null.");
    protos.add(proto);
    return this;
  }

  /**
   * Add a fully-qualified type to include in the parsed data. If specified, only these types and
   * their dependencies will be included. This allows for filtering message-heavy proto files such
   * that only desired message types are generated.
   * <p>
   * If no types are specified, every type in the specified proto files will be used.
   * <p>
   * It is an error to call this method with a type that is not contained in one of the proto files
   * being parsed.
   */
  public WireParser addTypeRoot(String type) {
    Preconditions.notNull(type, "Type must not be null.");
    Preconditions.isFalse(type.trim().isEmpty(), "Type must not be blank.");
    types.add(type);
    return this;
  }

  /**
   * Parse the supplied protos into an object model using the supplied information (or their
   * respective defaults).
   */
  public Set<ProtoFile> parse() throws IOException {
    validateInputFiles();

    // Obtain all of the directories and all of the protos that we care about.
    Set<File> directories = getOrFindDirectories();
    Set<File> protos = getOrFindProtos(directories);

    // Load the protos and any transitive dependencies they refer to.
    Set<ProtoFile> protoFiles = loadProtos(directories, protos);
    // Pass through the protos and collect all their fully-qualified types.
    Set<String> allTypes = collectAllTypes(protoFiles);
    // Update all type references in the proto files to be fully-qualified.
    protoFiles = fullyQualifyProtos(protoFiles, allTypes);

    if (!types.isEmpty()) {
      // Filter the protos to only include the specified types and their transitive dependencies.
      protoFiles = RootsFilter.filter(protoFiles, types);
    }
    return protoFiles;
  }

  /** Verify all directories and protos exist and are valid file types. */
  void validateInputFiles() {
    // Validate all directories exist and are actually directories.
    for (File directory : directories) {
      Preconditions.isTrue(fs.exists(directory), "Directory \"" + directory + "\" does not exist.");
      Preconditions.isTrue(fs.isDirectory(directory), "\"" + directory + "\" is not a directory.");
    }
    // Validate all protos exist and are files.
    for (File proto : protos) {
      Preconditions.isTrue(fs.exists(proto), "Proto \"" + proto + "\" does not exist.");
      Preconditions.isTrue(fs.isFile(proto), "Proto \"" + proto + "\" is not a file.");
    }
  }

  /** Returns the set of supplied directories or only the current working directory. */
  Set<File> getOrFindDirectories() {
    if (!directories.isEmpty()) {
      return unmodifiableSet(directories);
    }

    // No directories given. Grab the user's working directory as the sole directory.
    String userDir = System.getProperty("user.dir");
    Preconditions.notNull(userDir, "Unable to determine working directory.");
    return unmodifiableSet(singleton(new File(userDir)));
  }

  /** Returns the set of supplied proto files or all files under every directory. */
  Set<File> getOrFindProtos(Set<File> directories) {
    if (!protos.isEmpty()) {
      return unmodifiableSet(protos);
    }

    // No protos were explicitly given. Find all .proto files in each available directory.
    Set<File> protos = new LinkedHashSet<File>();

    Set<File> seenDirs = new LinkedHashSet<File>();
    Deque<File> dirQueue = new ArrayDeque<File>(directories);
    while (!dirQueue.isEmpty()) {
      File visitDir = dirQueue.removeLast();
      File[] files = fs.listFiles(visitDir);
      if (files != null) {
        for (File file : files) {
          if (fs.isDirectory(file) && !seenDirs.contains(file)) {
            seenDirs.add(file); // Prevent infinite recursion due to links.
            dirQueue.addLast(file);
          } else if (file.getName().endsWith(".proto")) {
            protos.add(file);
          }
        }
      }
    }
    return unmodifiableSet(protos);
  }

  /**
   * Returns a set of all protos from the supplied set parsed into an object model, searching in
   * the supplied directories for any dependencies.
   */
  private Set<ProtoFile> loadProtos(Set<File> directories, Set<File> protos) throws IOException {
    Set<ProtoFile> protoFiles = new LinkedHashSet<ProtoFile>();

    Deque<File> protoQueue = new ArrayDeque<File>(protos);
    Set<File> seenProtos = new LinkedHashSet<File>();
    while (!protoQueue.isEmpty()) {
      File proto = protoQueue.removeFirst();
      seenProtos.add(proto);

      String protoContent = fs.contentsUtf8(proto);
      ProtoFile protoFile = ProtoSchemaParser.parse(proto.getName(), protoContent);
      protoFiles.add(protoFile);

      // Queue all unseen dependencies to be resolved.
      for (String dependency : protoFile.getDependencies()) {
        File dependencyFile = resolveDependency(proto, directories, dependency);
        if (!seenProtos.contains(dependencyFile)) {
          protoQueue.addLast(dependencyFile);
        }
      }
    }
    return protoFiles;
  }

  /** Attempts to find a dependency's proto file in the supplied directories. */
  File resolveDependency(File proto, Set<File> directories, String dependency) {
    for (File directory : directories) {
      File dependencyFile = new File(directory, dependency);
      if (fs.exists(dependencyFile)) {
        return dependencyFile;
      }
    }

    StringBuilder error = new StringBuilder() //
        .append("Cannot resolve dependency \"")
        .append(dependency)
        .append("\" from \"")
        .append(proto.getAbsolutePath())
        .append("\" in:");
    for (File directory : directories) {
      error.append("\n  * ").append(directory.getAbsolutePath());
    }
    throw new IllegalStateException(error.toString());
  }
}
