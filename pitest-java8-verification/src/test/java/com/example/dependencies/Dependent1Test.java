package com.example.dependencies;

import org.junit.Assert;
import org.junit.Test;

public class Dependent1Test {
  @Test
  public void test(){
    Assert.assertEquals(5,DependentClass.getk());
  }
}
