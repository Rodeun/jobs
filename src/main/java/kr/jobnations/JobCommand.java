package kr.jobnations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class JobCommand implements TabExecutor {
    private final JobNationsPlugin plugin;
    private final JobManager jobs;

    public JobCommand(JobNationsPlugin plugin, JobManager jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    private static void msg(CommandSender s, String text, NamedTextColor color) {
        s.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equals("정보") || args[0].equalsIgnoreCase("info")) {
            if (sender instanceof Player p) {
                Job cur = jobs.getJob(p.getUniqueId());
                if (cur == null) {
                    msg(sender, "아직 직업이 없습니다. /직업 목록 으로 확인하세요.", NamedTextColor.YELLOW);
                } else {
                    msg(sender, "현재 직업: " + cur.display(), NamedTextColor.GREEN);
                    msg(sender, "메리트: " + cur.merit(), NamedTextColor.AQUA);
                    long rem = jobs.remainingMillis(p.getUniqueId());
                    if (rem > 0) msg(sender, "변경 가능까지: " + formatTime(rem), NamedTextColor.GRAY);
                    else msg(sender, "지금 직업을 변경할 수 있습니다.", NamedTextColor.GRAY);
                }
            } else {
                msg(sender, "사용법: /직업 <목록|정보|직업명>", NamedTextColor.YELLOW);
            }
            return true;
        }

        String sub = args[0];

        if (sub.equals("목록") || sub.equalsIgnoreCase("list")) {
            for (Job j : Job.values()) {
                msg(sender, "[" + j.display() + "] +" + j.merit() + " / -" + j.demerit(), NamedTextColor.WHITE);
            }
            return true;
        }

        if (sub.equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("jobnations.admin")) {
                msg(sender, "권한이 없습니다.", NamedTextColor.RED);
                return true;
            }
            plugin.reloadConfig();
            jobs.load();
            msg(sender, "설정을 다시 불러왔습니다.", NamedTextColor.GREEN);
            return true;
        }

        if (sub.equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("jobnations.admin")) {
                msg(sender, "권한이 없습니다.", NamedTextColor.RED);
                return true;
            }
            if (args.length < 2) {
                msg(sender, "사용법: /직업 reset <접속 중인 플레이어>", NamedTextColor.YELLOW);
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "해당 플레이어가 접속 중이 아닙니다.", NamedTextColor.RED);
                return true;
            }
            jobs.clear(target.getUniqueId());
            msg(sender, target.getName() + "의 직업과 쿨타임을 초기화했습니다.", NamedTextColor.GREEN);
            msg(target, "직업이 초기화되었습니다. 다시 선택할 수 있습니다.", NamedTextColor.YELLOW);
            return true;
        }

        Job job = Job.fromInput(sub);
        if (job == null) {
            msg(sender, "알 수 없는 직업입니다. /직업 목록 을 확인하세요.", NamedTextColor.RED);
            return true;
        }
        if (!(sender instanceof Player p)) {
            msg(sender, "플레이어만 직업을 선택할 수 있습니다.", NamedTextColor.RED);
            return true;
        }

        Job cur = jobs.getJob(p.getUniqueId());
        if (cur == job) {
            msg(sender, "이미 " + job.display() + "입니다.", NamedTextColor.YELLOW);
            return true;
        }
        if (cur != null) {
            long rem = jobs.remainingMillis(p.getUniqueId());
            if (rem > 0) {
                msg(sender, "직업 변경까지 " + formatTime(rem) + " 남았습니다.", NamedTextColor.RED);
                return true;
            }
        }

        jobs.setJob(p.getUniqueId(), job);
        msg(sender, "직업이 [" + job.display() + "](으)로 설정되었습니다.", NamedTextColor.GREEN);
        msg(sender, "메리트: " + job.merit(), NamedTextColor.AQUA);
        return true;
    }

    private static String formatTime(long ms) {
        long totalMin = ms / 60_000L;
        long days = totalMin / 1440;
        long hours = (totalMin % 1440) / 60;
        long mins = totalMin % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("일 ");
        if (hours > 0 || days > 0) sb.append(hours).append("시간 ");
        sb.append(mins).append("분");
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>();
        for (Job j : Job.values()) out.add(j.display());
        out.add("목록");
        out.add("정보");
        if (sender.hasPermission("jobnations.admin")) {
            out.add("reload");
            out.add("reset");
        }
        String prefix = args[0].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(prefix));
        return out;
    }
}
