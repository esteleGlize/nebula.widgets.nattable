/*******************************************************************************
 * Copyright (c) 2012 Original authors and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Original authors and others - initial API and implementation
 ******************************************************************************/
package org.eclipse.nebula.widgets.nattable.test.fixture.command;

import org.eclipse.nebula.widgets.nattable.command.ILayerCommand;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;

public class LayerCommandFixture implements ILayerCommand {

    private ILayer targetLayer;

    @Override
    public boolean convertToTargetLayer(ILayer targetLayer) {
        this.targetLayer = targetLayer;
        return true;
    }

    public ILayer getTargetLayer() {
        return this.targetLayer;
    }

    @Override
    public LayerCommandFixture cloneCommand() {
        return this;
    }

}
