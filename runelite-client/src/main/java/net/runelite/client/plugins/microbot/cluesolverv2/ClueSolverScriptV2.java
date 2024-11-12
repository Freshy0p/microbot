package net.runelite.client.plugins.microbot.cluesolverv2;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cluesolverv2.taskinterface.ClueTask;
import net.runelite.client.plugins.microbot.cluesolverv2.tasks.EmoteClueTask;
import net.runelite.client.plugins.microbot.cluesolverv2.util.ClueHelperV2;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClueSolverScriptV2 extends Script {

    private ScheduledExecutorService executor;
    private ClueSolverConfig config;

    @Inject
    private Client client;

    @Inject
    private ClueScrollPlugin clueScrollPlugin;

    @Inject
    private ClueHelperV2 clueHelper;

    @Inject
    private EventBus eventBus;

    private ClueTask currentTask;

    @Inject
    private Provider<EmoteClueTask> emoteClueTaskProvider; // Injected Provider

    public void start(ClueSolverConfig config) {
        this.config = config;
        executor = Executors.newScheduledThreadPool(10);
        executor.scheduleWithFixedDelay(this::runTaskFlow, 50, config.taskInterval(), TimeUnit.MILLISECONDS);
        eventBus.register(this);
        log.info("Clue Solver Script V2 started with task interval: {} ms", config.taskInterval());
    }

    public void stop() {
        if (currentTask != null) {
            currentTask.stop();
            currentTask = null;
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        eventBus.unregister(this);
        log.info("Clue Solver Script V2 stopped.");
    }

    private void runTaskFlow() {
        Microbot.enableAutoRunOn = false;
        loadNpcData();
        try {
            if (currentTask == null) {
                ClueScroll activeClue = clueScrollPlugin.getClue();
                if (activeClue != null && config.toggleAll()) {
                    log.info("New clue received: {}", activeClue);
                    currentTask = createTask(activeClue);
                    if (currentTask != null) {
                        currentTask.start();
                    }
                }
            } else {
                log.info("Executing current task: {}", currentTask.getTaskDescription());
                boolean isComplete = currentTask.execute();
                if (isComplete) {
                    log.info("Current clue task completed.");
                    currentTask.stop();
                    currentTask = null;
                }
            }
        } catch (Exception e) {
            log.error("Error in ClueSolverScriptV2", e);
            currentTask = null;
        }
    }

    private ClueTask createTask(ClueScroll clue) {
        log.info("Defining task for clue type: {}", clue.getClass().getSimpleName());

        if (clue instanceof EmoteClue) {
            EmoteClueTask task = emoteClueTaskProvider.get();
            task.setClue((EmoteClue) clue);
            return task;
        }

        // Additional clue types can be handled here with else-if blocks as needed
        log.warn("No matching task found for clue type: {}", clue.getClass().getSimpleName());
        return null;
    }

    private void loadNpcData() {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load NPC data", e);
        }
    }


    public void updateConfig(ClueSolverConfig config) {
        this.config = config;
    }
}
