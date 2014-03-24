/* Copyright 2014 hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import java.util.UUID;

/**
 * Generates a UUID.
 * 
 * @author Pascal Christoph (dr0i)
 * 
 */
public final class GenerateUuid extends AbstractGetter {

	public String process() {
		return UUID.randomUUID().toString();
	}
}
