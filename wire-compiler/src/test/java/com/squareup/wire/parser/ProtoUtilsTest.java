package com.squareup.wire.parser;

import com.google.common.collect.ImmutableSet;
import com.squareup.protoparser.MessageType;
import com.squareup.protoparser.ProtoFile;
import com.squareup.protoparser.Type;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static com.squareup.wire.TestUtil.NO_EXTENDS;
import static com.squareup.wire.TestUtil.NO_EXTENSIONS;
import static com.squareup.wire.TestUtil.NO_FIELDS;
import static com.squareup.wire.TestUtil.NO_OPTIONS;
import static com.squareup.wire.TestUtil.NO_SERVICES;
import static com.squareup.wire.TestUtil.NO_STRINGS;
import static com.squareup.wire.TestUtil.NO_TYPES;
import static com.squareup.wire.TestUtil.protoResource;
import static com.squareup.wire.parser.ProtoUtils.resolveType;
import static java.util.Arrays.asList;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.fail;

public class ProtoUtilsTest {
  @Test public void resolvePrimitivesAreReturned() {
    Set<String> allTypes = Collections.emptySet();
    assertThat(resolveType(allTypes, "foo.Bar", "bool")).isEqualTo("bool");
    assertThat(resolveType(allTypes, "foo.Bar", "int64")).isEqualTo("int64");
    assertThat(resolveType(allTypes, "foo.Bar", "string")).isEqualTo("string");
  }

  @Test public void resolveSeenFullyQualifiedTypeIsReturned() {
    Set<String> allTypes = ImmutableSet.of("foo.Bar");
    assertThat(resolveType(allTypes, "ping.pong", "foo.Bar")).isEqualTo("foo.Bar");
  }

  @Test public void resolveChecksParentScopes() {
    Set<String> allTypes = ImmutableSet.of("foo.Bar");
    assertThat(resolveType(allTypes, "foo.Bar.Ping.Pong", "Bar")).isEqualTo("foo.Bar");
  }

  @Test public void resolveSupportsAbsoluteTypes() {
    Set<String> allTypes = ImmutableSet.of("foo.Baz", "bar.Baz");
    assertThat(resolveType(allTypes, "foo", ".bar.Baz")).isEqualTo("bar.Baz");
  }

  @Test public void resolveThrowsIfMissing() {
    Set<String> allTypes = Collections.emptySet();
    try {
      resolveType(allTypes, "foo.Bar", "MissingType");
      fail("Missing type should throw exception.");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessage("Unknown type MissingType in foo.Bar");
    }
  }

  @Test public void collectAllTypesRecursesToNestedTypes() {
    Set<ProtoFile> protos = ImmutableSet.of(protoResource("person.proto"));
    Set<String> types = ProtoUtils.collectAllTypes(protos);
    assertThat(types).containsOnly( //
        "wire.Person", //
        "wire.Person.PhoneType", //
        "wire.Person.PhoneNumber");
  }

  @Test public void collectAllTypesFailsOnDuplicates() {
    Type message =
        new MessageType("Message", "wire.Message", "", NO_FIELDS, NO_TYPES, NO_EXTENSIONS,
            NO_OPTIONS);
    ProtoFile file1 =
        new ProtoFile("file1.proto", "wire", NO_STRINGS, NO_STRINGS, asList(message), NO_SERVICES,
            NO_OPTIONS, NO_EXTENDS);
    ProtoFile file2 =
        new ProtoFile("file2.proto", "wire", NO_STRINGS, NO_STRINGS, asList(message), NO_SERVICES,
            NO_OPTIONS, NO_EXTENDS);
    Set<ProtoFile> files = ImmutableSet.of(file1, file2);

    try {
      ProtoUtils.collectAllTypes(files);
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Duplicate type wire.Message defined in file1.proto, file2.proto");
    }
  }
}
