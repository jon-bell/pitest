package org.pitest.mutationtest;

import org.pitest.classpath.CodeSource;
import org.pitest.coverage.CoverageDatabase;

public interface CoverageListener {
	public void handleCoverageData(CodeSource code, CoverageDatabase coverage);
}
