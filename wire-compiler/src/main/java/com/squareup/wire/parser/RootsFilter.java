package com.squareup.wire.parser;

import com.squareup.protoparser.EnumType;
import com.squareup.protoparser.MessageType;
import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.Service;
import com.squareup.protoparser.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.squareup.wire.parser.ProtoUtils.isPrimitiveType;

final class RootsFilter {
  static Set<ProtoFile> filter(Set<ProtoFile> protoFiles, Set<String> roots) {
    // Transform the set of proto files into a tree of nodes.
    RootNode rootNode = new RootNode(protoFiles);
    // Map to every node from its fully-qualified type (if any).
    Map<String, Node<?>> nodeMap = rootNode.asNodeMap();

    // Collect nodes to keep by starting at the supplied roots and transitively iterating out.
    Set<Node<?>> nodesToKeep = new LinkedHashSet<Node<?>>();
    for (String root : roots) {
      if (!nodeMap.containsKey(root)) {
        throw new IllegalStateException("Unknown type " + root);
      }
      nodeMap.get(root).keepNodes(nodesToKeep, nodeMap);
    }

    // Re-assemble all of the marked nodes back into a set of proto files.
    return rootNode.reifyKeptNodes(nodesToKeep);
  }

  private static Node<?> nodeForType(Node<?> parent, Type type) {
    if (type instanceof MessageType) {
      return new MessageTypeNode(parent, (MessageType) type);
    }
    if (type instanceof EnumType) {
      return new EnumTypeNode(parent, (EnumType) type);
    }
    throw new IllegalArgumentException("Unknown type " + type.getClass().getCanonicalName());
  }

  private abstract static class Node<T> {
    final Node<?> parent;
    final String type;
    final T obj;
    final List<Node<?>> children;

    Node(Node<?> parent, String type, T obj) {
      this.parent = parent;
      this.type = type;
      this.obj = obj;

      children = new ArrayList<Node<?>>();
    }

    /** Flatten this type and the types of any children into a map to their corresponding nodes. */
    final Map<String, Node<?>> asNodeMap() {
      Map<String, Node<?>> typeMap = new LinkedHashMap<String, Node<?>>();
      if (type != null) {
        typeMap.put(type, this);
      }
      for (Node<?> child : children) {
        typeMap.putAll(child.asNodeMap());
      }
      return typeMap;
    }

    /** Create a real proto object of this type and any children present in the supplied set. */
    abstract T reifyKeptNodes(Set<Node<?>> typesToKeep);

    /** Mark this node to be kept. This method should be overriden to keep any dependencies. */
    void keepNodes(Set<Node<?>> typesToKeep, Map<String, Node<?>> nodeMap) {
      if (typesToKeep.contains(this)) {
        return;
      }
      typesToKeep.add(this);
      if (parent != null) {
        parent.keepNodes(typesToKeep, nodeMap);
      }
    }
  }

  /** The root node which represents set of {@link ProtoFile} objects. */
  private static class RootNode extends Node<Set<ProtoFile>> {
    RootNode(Set<ProtoFile> protoFiles) {
      super(null, null, protoFiles);

      for (ProtoFile protoFile : protoFiles) {
        children.add(new ProtoFileNode(this, protoFile));
      }
    }

    @Override public Set<ProtoFile> reifyKeptNodes(Set<Node<?>> typesToKeep) {
      Set<ProtoFile> protoFiles = new LinkedHashSet<ProtoFile>();
      for (Node<?> child : children) {
        if (typesToKeep.contains(child)) {
          protoFiles.add((ProtoFile) child.reifyKeptNodes(typesToKeep));
        }
      }
      return protoFiles;
    }
  }

  private static class ProtoFileNode extends Node<ProtoFile> {
    ProtoFileNode(RootNode parent, ProtoFile protoFile) {
      super(parent, null, protoFile);

      for (Type type : protoFile.getTypes()) {
        children.add(nodeForType(this, type));
      }
      for (Service service : protoFile.getServices()) {
        children.add(new ServiceNode(this, service));
      }
    }

    @Override ProtoFile reifyKeptNodes(Set<Node<?>> typesToKeep) {
      List<Type> markedTypes = new ArrayList<Type>();
      List<Service> markedServices = new ArrayList<Service>();
      for (Node<?> child : children) {
        if (typesToKeep.contains(child)) {
          if (child instanceof ServiceNode) {
            markedServices.add((Service) child.reifyKeptNodes(typesToKeep));
          } else {
            markedTypes.add((Type) child.reifyKeptNodes(typesToKeep));
          }
        }
      }
      return new ProtoFile(obj.getFileName(), obj.getPackageName(), obj.getDependencies(),
          obj.getPublicDependencies(), markedTypes, markedServices, obj.getOptions(),
          obj.getExtendDeclarations());
    }
  }

  private static class ServiceNode extends Node<Service> {
    ServiceNode(Node<?> parent, Service type) {
      super(parent, type.getFullyQualifiedName(), type);
    }

    @Override void keepNodes(Set<Node<?>> typesToKeep, Map<String, Node<?>> nodeMap) {
      super.keepNodes(typesToKeep, nodeMap);

      for (Service.Method method : obj.getMethods()) {
        String requestType = method.getRequestType();
        if (!isPrimitiveType(requestType)) {
          nodeMap.get(requestType).keepNodes(typesToKeep, nodeMap);
        }
        String responseType = method.getResponseType();
        if (!isPrimitiveType(responseType)) {
          nodeMap.get(responseType).keepNodes(typesToKeep, nodeMap);
        }
      }
    }

    @Override Service reifyKeptNodes(Set<Node<?>> typesToKeep) {
      return obj; // No child types that could possibly be filtered. Return the original.
    }
  }

  private static class MessageTypeNode extends Node<MessageType> {
    MessageTypeNode(Node<?> parent, MessageType type) {
      super(parent, type.getFullyQualifiedName(), type);

      for (Type nestedType : type.getNestedTypes()) {
        children.add(nodeForType(this, nestedType));
      }
    }

    @Override void keepNodes(Set<Node<?>> typesToKeep, Map<String, Node<?>> nodeMap) {
      super.keepNodes(typesToKeep, nodeMap);

      for (MessageType.Field field : obj.getFields()) {
        String fieldType = field.getType();
        if (!isPrimitiveType(fieldType)) {
          nodeMap.get(fieldType).keepNodes(typesToKeep, nodeMap);
        }
      }
    }

    @Override MessageType reifyKeptNodes(Set<Node<?>> typesToKeep) {
      List<Type> markedNestedTypes = new ArrayList<Type>();
      for (Node<?> child : children) {
        if (typesToKeep.contains(child)) {
          markedNestedTypes.add((Type) child.reifyKeptNodes(typesToKeep));
        }
      }
      return new MessageType(obj.getName(), obj.getFullyQualifiedName(), obj.getDocumentation(),
          obj.getFields(), markedNestedTypes, obj.getExtensions(), obj.getOptions());
    }
  }

  private static class EnumTypeNode extends Node<EnumType> {
    EnumTypeNode(Node<?> parent, EnumType type) {
      super(parent, type.getFullyQualifiedName(), type);
    }

    @Override EnumType reifyKeptNodes(Set<Node<?>> typesToKeep) {
      return obj; // No child types that could possibly be filtered. Return the original.
    }
  }

  private RootsFilter() {
    throw new AssertionError("No instances.");
  }
}
