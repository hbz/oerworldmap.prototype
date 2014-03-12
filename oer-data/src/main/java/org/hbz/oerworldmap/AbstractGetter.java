/* Copyright 2014 hbz, Pascal Christoph.
 * Licensed under the Eclipse Public License 1.0 */
package org.hbz.oerworldmap;

import org.culturegraph.mf.morph.NamedValueSource;
import org.culturegraph.mf.morph.functions.AbstractFunction;
import org.culturegraph.mf.morph.functions.Function;

/**
 * Baseclass for {@link Function}s. Don't need any input to generate output.
 * 
 * @author Pascal Christoph (dr0i)
 */
public abstract class AbstractGetter extends AbstractFunction {

	protected abstract String process();

	@Override
	public final void receive(final String name, final String value, final NamedValueSource source,
			final int recordCount, final int entityCount) {
		final String processedValue = process();
		if (processedValue == null) {
			return;
		}
		getNamedValueReceiver().receive(name, processedValue, source, recordCount, entityCount);
	}
}
