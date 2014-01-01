package com.squareup.wire.parser;

final class Preconditions {
  static void isTrue(boolean value, String message) {
    if (!value) {
      throw new IllegalStateException(message);
    }
  }

  static void isFalse(boolean value, String message) {
    if (value) {
      throw new IllegalStateException(message);
    }
  }

  static void notNull(Object o, String message) {
    if (o == null) {
      throw new NullPointerException(message);
    }
  }

  private Preconditions() {
    throw new AssertionError("No instances.");
  }
}
