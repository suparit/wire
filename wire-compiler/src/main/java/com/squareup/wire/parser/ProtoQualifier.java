package com.squareup.wire.parser;

import com.squareup.protoparser.EnumType;
import com.squareup.protoparser.ExtendDeclaration;
import com.squareup.protoparser.MessageType;
import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.Service;
import com.squareup.protoparser.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.squareup.protoparser.MessageType.Field;
import static com.squareup.protoparser.Service.Method;
import static java.util.Collections.unmodifiableSet;

final class ProtoQualifier {
  /** Update a set of profo files to only refer to fully-qualified or primitive types. */
  static Set<ProtoFile> fullyQualifyProtos(Set<ProtoFile> protoFiles, Set<String> allTypes) {
    Set<ProtoFile> qualifiedProtoFiles = new LinkedHashSet<ProtoFile>(protoFiles.size());
    for (ProtoFile protoFile : protoFiles) {
      // Replace all types (including nested ones) with fully-qualified references.
      List<Type> types = protoFile.getTypes();
      List<Type> qualifiedTypes = new ArrayList<Type>(types.size());
      for (Type type : types) {
        qualifiedTypes.add(fullyQualifyType(type, allTypes));
      }

      // Replace all services with fully-qualified references.
      List<Service> services = protoFile.getServices();
      List<Service> qualifiedServices = new ArrayList<Service>(services.size());
      for (Service service : services) {
        qualifiedServices.add(fullyQualifyService(service, allTypes));
      }

      // Replace all extend declarations with fully-qualified references.
      List<ExtendDeclaration> extendDeclarations = protoFile.getExtendDeclarations();
      List<ExtendDeclaration> qualifiedExtendDeclarations =
          new ArrayList<ExtendDeclaration>(extendDeclarations.size());
      for (ExtendDeclaration extendDeclaration : extendDeclarations) {
        qualifiedExtendDeclarations.add(fullyQualifyExtendDeclaration(extendDeclaration, allTypes));
      }

      // Create a new proto file using our new types, services, and extends.
      qualifiedProtoFiles.add(new ProtoFile(protoFile.getFileName(), protoFile.getPackageName(),
          protoFile.getDependencies(), protoFile.getPublicDependencies(), qualifiedTypes,
          qualifiedServices, protoFile.getOptions(), qualifiedExtendDeclarations));
    }

    return unmodifiableSet(qualifiedProtoFiles);
  }

  /** Update a message or enum type to only refer to fully-qualified or primitive types. */
  static Type fullyQualifyType(Type type, Set<String> allTypes) {
    if (type instanceof MessageType) {
      MessageType messageType = (MessageType) type;

      // Recurse to fully-qualify and nested types.
      List<Type> nestedTypes = type.getNestedTypes();
      List<Type> qualifiedNestedTypes = new ArrayList<Type>(nestedTypes.size());
      for (Type nestedType : nestedTypes) {
        qualifiedNestedTypes.add(fullyQualifyType(nestedType, allTypes));
      }

      // Fully-qualify each field's type.
      String qualifiedName = messageType.getFullyQualifiedName();
      List<Field> fields = messageType.getFields();
      List<Field> qualifiedFields = fullyQualifyFields(fields, qualifiedName, allTypes);

      // Create a new message using our new nested types and fields.
      return new MessageType(messageType.getName(), messageType.getFullyQualifiedName(),
          messageType.getDocumentation(), qualifiedFields, qualifiedNestedTypes,
          messageType.getExtensions(), messageType.getOptions());
    } else if (type instanceof EnumType) {
      return type; // Enums don't have any type references that need qualified.
    } else {
      throw new IllegalArgumentException("Unknown type " + type.getClass().getCanonicalName());
    }
  }

  /** Update an service to only refer to fully-qualified or primitive types. */
  static Service fullyQualifyService(Service service, Set<String> allTypes) {
    String qualifiedName = service.getFullyQualifiedName();
    List<Method> methods = service.getMethods();
    List<Method> qualifiedMethods = new ArrayList<Method>(methods.size());
    for (Method method : methods) {
      String newRequestType =
          ProtoUtils.resolveType(allTypes, qualifiedName, method.getRequestType());
      String newResponseType =
          ProtoUtils.resolveType(allTypes, qualifiedName, method.getResponseType());

      qualifiedMethods.add(
          new Method(method.getName(), method.getDocumentation(), newRequestType, newResponseType,
              method.getOptions()));
    }

    return new Service(service.getName(), service.getFullyQualifiedName(),
        service.getDocumentation(), service.getOptions(), qualifiedMethods);
  }

  /** Update an extend declaration to only refer to fully-qualified or primitive types. */
  static ExtendDeclaration fullyQualifyExtendDeclaration(ExtendDeclaration extendDeclaration,
      Set<String> allTypes) {
    String qualifiedName = extendDeclaration.getFullyQualifiedName();
    List<Field> fields = extendDeclaration.getFields();
    List<Field> qualifiedFields = fullyQualifyFields(fields, qualifiedName, allTypes);

    return new ExtendDeclaration(extendDeclaration.getName(),
        extendDeclaration.getFullyQualifiedName(), extendDeclaration.getDocumentation(),
        qualifiedFields);
  }

  /** Update a list of fields to only refer to fully-qualified or primitive types. */
  private static List<Field> fullyQualifyFields(List<Field> fields, String scope,
      Set<String> allTypes) {
    List<Field> qualifiedFields = new ArrayList<Field>(fields.size());
    for (Field field : fields) {
      String newType = ProtoUtils.resolveType(allTypes, scope, field.getType());

      qualifiedFields.add(new Field(field.getLabel(), newType, field.getName(), field.getTag(),
          field.getDocumentation(), field.getOptions()));
    }
    return qualifiedFields;
  }

  private ProtoQualifier() {
    throw new AssertionError("No instances.");
  }
}
