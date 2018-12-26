package org.pitest.classinfo;

import java.io.Serializable;

public interface HashFunction extends Serializable {

  long hash(byte[] value);

}
