package buildcraft.transport.pipe.flow;

import net.minecraft.util.EnumFacing;

import buildcraft.api.transport.pipe.IFlowPower;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEventPower;

public class PrePowerSendEvent extends PipeEventPower {
    private final PipeFlowPower.Section section;
    private final EnumFacing from;

    public PrePowerSendEvent(IPipeHolder holder, IFlowPower flow, PipeFlowPower.Section section, EnumFacing from) {
        super(holder, flow);
        this.section = section;
        this.from = from;
    }

    public PipeFlowPower.Section getSection() {
        return section;
    }

    public EnumFacing getFrom() {
        return from;
    }
}

