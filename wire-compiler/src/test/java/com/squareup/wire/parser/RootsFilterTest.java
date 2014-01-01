package com.squareup.wire.parser;

import com.google.common.collect.ImmutableSet;
import com.squareup.protoparser.ProtoFile;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static com.squareup.wire.TestUtil.protoResource;
import static com.squareup.wire.parser.ProtoUtils.collectAllTypes;
import static com.squareup.wire.parser.ProtoQualifier.fullyQualifyProtos;
import static org.fest.assertions.api.Assertions.assertThat;

public class RootsFilterTest {
  private Set<ProtoFile> protoFiles;

  @Before public void setUp() {
    Set<ProtoFile> protoFiles = ImmutableSet.of(protoResource("roots.proto"));
    Set<String> allTypes = collectAllTypes(protoFiles);
    this.protoFiles = fullyQualifyProtos(protoFiles, allTypes);
  }

  private Set<String> filter(String... keep) {
    Set<String> roots = ImmutableSet.copyOf(keep);
    Set<ProtoFile> filtered = RootsFilter.filter(protoFiles, roots);
    return collectAllTypes(filtered);
  }

  @Test public void transitive() {
    assertThat(filter("wire.A")) //
        .containsOnly("wire.A", "wire.B", "wire.C", "wire.D");
  }

  @Test public void child() {
    assertThat(filter("wire.E")) //
        .containsOnly("wire.E", "wire.E.F", "wire.G");
  }

  @Test public void parents() {
    assertThat(filter("wire.H")) //
        .containsOnly("wire.H", "wire.E", "wire.E.F", "wire.G");
  }

  @Test public void none() {
    assertThat(filter("wire.I")) //
        .containsOnly("wire.I");
  }
}
