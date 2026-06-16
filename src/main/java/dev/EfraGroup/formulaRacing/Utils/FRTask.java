package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public interface FRTask extends Consumer<FRTask> {
    Plugin getOwningPlugin();
    boolean isRepeating();
    void cancel();
    boolean isCancelled();
    
    @Override
    void accept(FRTask task);
}
