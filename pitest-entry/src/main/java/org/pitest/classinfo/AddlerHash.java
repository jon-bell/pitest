package org.pitest.classinfo;

import java.util.zip.Adler32;

public class AddlerHash implements HashFunction {

  private static final long serialVersionUID = -1535867534525581253L;

  @Override
  public long hash(final byte[] value) {
    final Adler32 adler = new Adler32();
    adler.update(value);
    return adler.getValue();
  }

}
