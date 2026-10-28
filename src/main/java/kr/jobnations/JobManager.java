package kr.jobnations;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class JobManager {
    private final JobNationsPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public JobManager(JobNationsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("data.yml 저장 실패: " + e.getMessage());
        }
    }

    public Job getJob(UUID id) {
        String s = data.getString("players." + id + ".job");
        if (s == null) return null;
        try {
            return Job.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setJob(UUID id, Job job) {
        data.set("players." + id + ".job", job.name());
        data.set("players." + id + ".changed", System.currentTimeMillis());
        save();
    }

    public void clear(UUID id) {
        data.set("players." + id, null);
        save();
    }

    /** 남은 쿨타임(ms). 0이면 변경 가능. */
    public long remainingMillis(UUID id) {
        long cooldown = (long) (plugin.getConfig().getDouble("change-cooldown-hours", 48) * 3_600_000L);
        long changed = data.getLong("players." + id + ".changed", 0L);
        return Math.max(0L, changed + cooldown - System.currentTimeMillis());
    }
}
