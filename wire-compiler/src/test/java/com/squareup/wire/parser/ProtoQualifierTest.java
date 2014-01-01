package com.squareup.wire.parser;

import com.google.common.collect.ImmutableSet;
import com.squareup.protoparser.EnumType;
import com.squareup.protoparser.ExtendDeclaration;
import com.squareup.protoparser.MessageType;
import com.squareup.protoparser.Service;
import com.squareup.protoparser.Type;
import java.util.Set;
import org.junit.Test;

import static com.squareup.protoparser.MessageType.Label.REQUIRED;
import static com.squareup.wire.TestUtil.NO_EXTENSIONS;
import static com.squareup.wire.TestUtil.NO_OPTIONS;
import static com.squareup.wire.TestUtil.NO_TYPES;
import static com.squareup.wire.parser.ProtoQualifier.fullyQualifyExtendDeclaration;
import static com.squareup.wire.parser.ProtoQualifier.fullyQualifyService;
import static com.squareup.wire.parser.ProtoQualifier.fullyQualifyType;
import static java.util.Arrays.asList;
import static org.fest.assertions.api.Assertions.assertThat;

public class ProtoQualifierTest {
  @Test public void fullyQualifyMessageTest() {
    Set<String> allTypes = ImmutableSet.of( //
        "wire.One", //
        "wire.Two", //
        "wire.Three" //
    );

    EnumType.Value value = new EnumType.Value("FOO", 1, "", NO_OPTIONS);
    Type enumType = new EnumType("Enum", "wire.Enum", "", NO_OPTIONS, asList(value));

    MessageType.Field inner1 = new MessageType.Field(REQUIRED, "Three", "three", 1, "", NO_OPTIONS);
    Type inner = new MessageType("Nested", "wire.Message.Nested", "", asList(inner1), NO_TYPES,
        NO_EXTENSIONS, NO_OPTIONS);

    MessageType.Field f1 = new MessageType.Field(REQUIRED, "One", "one", 1, "", NO_OPTIONS);
    MessageType.Field f2 = new MessageType.Field(REQUIRED, "Two", "two", 2, "", NO_OPTIONS);
    Type message =
        new MessageType("Message", "wire.Message", "", asList(f1, f2), asList(enumType, inner),
            NO_EXTENSIONS, NO_OPTIONS);

    MessageType.Field expectedInner1 =
        new MessageType.Field(REQUIRED, "wire.Three", "three", 1, "", NO_OPTIONS);
    Type expectedInner =
        new MessageType("Nested", "wire.Message.Nested", "", asList(expectedInner1), NO_TYPES,
            NO_EXTENSIONS, NO_OPTIONS);

    MessageType.Field expected1 =
        new MessageType.Field(REQUIRED, "wire.One", "one", 1, "", NO_OPTIONS);
    MessageType.Field expected2 =
        new MessageType.Field(REQUIRED, "wire.Two", "two", 2, "", NO_OPTIONS);
    Type expected = new MessageType("Message", "wire.Message", "", asList(expected1, expected2),
        asList(enumType, expectedInner), NO_EXTENSIONS, NO_OPTIONS);

    assertThat(fullyQualifyType(message, allTypes)).isEqualTo(expected);
  }

  @Test public void fullyQualifyServiceTest() {
    Set<String> allTypes = ImmutableSet.of( //
        "wire.Request1", //
        "wire.Response1", //
        "wire.Request2", //
        "wire.Response2" //
    );

    Service.Method m1 = new Service.Method("Method1", "", "Request1", "Response1", NO_OPTIONS);
    Service.Method m2 = new Service.Method("Method2", "", "Request2", "Response2", NO_OPTIONS);
    Service service = new Service("Service", "wire.Service", "", NO_OPTIONS, asList(m1, m2));

    Service.Method expected1 =
        new Service.Method("Method1", "", "wire.Request1", "wire.Response1", NO_OPTIONS);
    Service.Method expected2 =
        new Service.Method("Method2", "", "wire.Request2", "wire.Response2", NO_OPTIONS);
    Service expected =
        new Service("Service", "wire.Service", "", NO_OPTIONS, asList(expected1, expected2));

    assertThat(fullyQualifyService(service, allTypes)).isEqualTo(expected);
  }

  @Test public void fullyQualifyExtendTest() {
    Set<String> allTypes = ImmutableSet.of("wire.Foo");

    MessageType.Field f1 = new MessageType.Field(REQUIRED, "Foo", "foo", 1, "", NO_OPTIONS);
    ExtendDeclaration extend = new ExtendDeclaration("Bar", "wire.Bar", "", asList(f1));

    MessageType.Field expected1 =
        new MessageType.Field(REQUIRED, "wire.Foo", "foo", 1, "", NO_OPTIONS);
    ExtendDeclaration expected = new ExtendDeclaration("Bar", "wire.Bar", "", asList(expected1));

    assertThat(fullyQualifyExtendDeclaration(extend, allTypes)).isEqualTo(expected);
  }
}
