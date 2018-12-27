package com.example.coverage.execute.samples.dependencies;

import org.junit.Assert;
import org.junit.Test;

public class Test2 {
  @Test
  public void test(){
    Assert.assertEquals(0,Testee.flakilyCovered());
  }
}
