package com.squareup.wire;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.squareup.protoparser.EnumType;
import com.squareup.protoparser.ExtendDeclaration;
import com.squareup.protoparser.Extensions;
import com.squareup.protoparser.MessageType;
import com.squareup.protoparser.Option;
import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.ProtoSchemaParser;
import com.squareup.protoparser.Service;
import com.squareup.protoparser.Type;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class TestUtil {
  public static final List<Service> NO_SERVICES = Collections.emptyList();
  public static final List<ExtendDeclaration> NO_EXTENDS = Collections.emptyList();
  public static final List<String> NO_STRINGS = Collections.emptyList();
  public static final List<Option> NO_OPTIONS = Collections.emptyList();
  public static final List<EnumType.Value> NO_VALUES = Collections.emptyList();
  public static final List<Extensions> NO_EXTENSIONS = Collections.emptyList();
  public static final List<Type> NO_TYPES = Collections.emptyList();
  public static final List<MessageType.Field> NO_FIELDS = Collections.emptyList();

  public static ProtoFile protoResource(String name) {
    try {
      String data = Resources.asCharSource(Resources.getResource(name), Charsets.UTF_8).read();
      return ProtoSchemaParser.parse(name, data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TestUtil() {
    throw new AssertionError("No instances.");
  }
}
