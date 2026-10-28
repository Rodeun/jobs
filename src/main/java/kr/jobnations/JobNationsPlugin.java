package kr.jobnations;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class JobNationsPlugin extends JavaPlugin {
    private JobManager jobs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        jobs = new JobManager(this);
        jobs.load();

        getServer().getPluginManager().registerEvents(new JobListener(this, jobs), this);

        PluginCommand cmd = getCommand("job");
        if (cmd != null) {
            JobCommand jc = new JobCommand(this, jobs);
            cmd.setExecutor(jc);
            cmd.setTabCompleter(jc);
        }

        getServer().getScheduler().runTaskTimer(this, this::applyPassives, 40L, 40L);
        getLogger().info("JobNations 활성화");
    }

    private void applyPassives() {
        boolean nightVision = getConfig().getBoolean("merit.miner-night-vision", true);
        boolean dolphin = getConfig().getBoolean("merit.fisher-dolphins-grace", true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Job job = jobs.getJob(p.getUniqueId());
            if (job == null) continue;

            if (job == Job.MINER && nightVision
                    && p.getWorld().getEnvironment() == World.Environment.NORMAL
                    && p.getLocation().getBlock().getLightFromSky() < 4) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, true, false, false));
            }
            if (job == Job.FISHER && dolphin && p.isInWater()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 0, true, false, false));
            }
        }
    }
}
