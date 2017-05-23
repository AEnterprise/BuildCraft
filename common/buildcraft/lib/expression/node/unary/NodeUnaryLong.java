/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.expression.node.unary;

import java.util.function.LongUnaryOperator;

import buildcraft.lib.expression.NodeInliningHelper;
import buildcraft.lib.expression.api.IExpressionNode.INodeLong;
import buildcraft.lib.expression.node.value.NodeConstantLong;

public class NodeUnaryLong implements INodeLong {
    private final INodeLong from;
    private final LongUnaryOperator func;
    private final String op;

    public NodeUnaryLong(INodeLong from, LongUnaryOperator func, String op) {
        this.from = from;
        this.func = func;
        this.op = op;
    }

    @Override
    public long evaluate() {
        return func.applyAsLong(from.evaluate());
    }

    @Override
    public INodeLong inline() {
        return NodeInliningHelper.tryInline(this, from, (f) -> new NodeUnaryLong(f, func, op), //
            (f) -> new NodeConstantLong(func.applyAsLong(f.evaluate())));
    }

    @Override
    public String toString() {
        return op + "(" + from + ")";
    }
}
