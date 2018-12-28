package com.example.dependencies;

public class DependentClass {
  static int i;
  static int k;

  public static int getk() {
    if (i > 0) {
      k = k + 2;
      return k;
    } else {
      i = i + 1;
      return TargetDependentClass.mutant();
    }
  }
}
