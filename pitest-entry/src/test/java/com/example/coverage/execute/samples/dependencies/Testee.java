package com.example.coverage.execute.samples.dependencies;

public class Testee {

  static int i = 0;
  static int k;

  public static int flakilyCovered() {
    if (i > 0) {
      k = k + 1;
      return k;
    } else {
      i++;
      DependentTestee.aha();
      return k;
    }
  }
}
