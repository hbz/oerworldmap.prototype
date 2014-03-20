/* Copyright 2014 Pascal Christoph. Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Main test suite for all unit tests. Excluded online tests.
 * 
 * @author Pascal Christoph (dr0i)
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ OerJson2RdfWriterTest.class })
public final class UnitTests {
	/* Suite class, groups tests via annotation above */
}