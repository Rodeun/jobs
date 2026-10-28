package kr.jobnations;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class JobListener implements Listener {
    private final JobNationsPlugin plugin;
    private final JobManager jobs;

    public JobListener(JobNationsPlugin plugin, JobManager jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private static boolean chance(double c) {
        return c > 0 && ThreadLocalRandom.current().nextDouble() < c;
    }

    private static void bar(Player p, String text, NamedTextColor color) {
        p.sendActionBar(Component.text(text, color));
    }

    private static boolean isMatureCrop(Block b) {
        switch (b.getType()) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART:
                return b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
            case MELON, PUMPKIN, SUGAR_CANE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    // ---------- 농부 / 광부 : 블록 파괴 ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Job job = jobs.getJob(p.getUniqueId());
        if (job == null) return;

        Block b = e.getBlock();
        boolean crop = isMatureCrop(b);
        boolean ore = !crop && isOre(b.getType());
        if (!crop && !ore) return;

        Job owner = crop ? Job.FARMER : Job.MINER;
        double bonus = cfg().getDouble(crop ? "merit.farmer-crop-bonus-chance" : "merit.miner-ore-bonus-chance", 0.4);
        double loss = cfg().getDouble(crop ? "penalty.farmer-crop-loss-chance" : "penalty.miner-ore-loss-chance", 0.35);

        if (job == owner) {
            if (chance(bonus)) {
                ItemStack tool = p.getInventory().getItemInMainHand();
                for (ItemStack drop : b.getDrops(tool, p)) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop.clone());
                }
                bar(p, "[" + job.display() + "] 추가 드롭!", NamedTextColor.GREEN);
            }
        } else if (chance(loss)) {
            e.setDropItems(false);
            bar(p, owner.display() + "의 일이라 서툴러서 드롭을 잃었습니다.", NamedTextColor.RED);
        }
    }

    // ---------- 건축가 : 블록 설치 ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        Job job = jobs.getJob(p.getUniqueId());
        if (job == null) return;
        if (!e.getBlockPlaced().getType().isSolid()) return;

        if (job == Job.BUILDER) {
            if (chance(cfg().getDouble("merit.builder-refund-chance", 0.3))) {
                ItemStack refund = new ItemStack(e.getBlockPlaced().getType(), 1);
                p.getInventory().addItem(refund).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                bar(p, "[건축가] 블록 환급!", NamedTextColor.GREEN);
            }
        } else {
            float add = (float) cfg().getDouble("penalty.build-exhaustion", 0.5);
            p.setExhaustion(p.getExhaustion() + add);
        }
    }

    // ---------- 어부 : 낚시 ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        Job job = jobs.getJob(p.getUniqueId());
        if (job == null) return;
        if (!(e.getCaught() instanceof Item caught)) return;

        if (job == Job.FISHER) {
            if (chance(cfg().getDouble("merit.fisher-bonus-chance", 0.35))) {
                ItemStack extra = caught.getItemStack().clone();
                p.getInventory().addItem(extra).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                e.setExpToDrop(e.getExpToDrop() + cfg().getInt("merit.fisher-bonus-exp", 3));
                bar(p, "[어부] 대어!", NamedTextColor.GREEN);
            }
        } else if (chance(cfg().getDouble("penalty.fisher-loss-chance", 0.4))) {
            caught.remove();
            e.setExpToDrop(0);
            bar(p, "어부의 일이라 서툴러서 놓쳤습니다.", NamedTextColor.RED);
        }
    }

    // ---------- 사냥꾼 : 공격력 ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player p = null;
        if (e.getDamager() instanceof Player pl) {
            p = pl;
        } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
            p = pl;
        }
        if (p == null) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player || victim instanceof ArmorStand) return;

        Job job = jobs.getJob(p.getUniqueId());
        if (job == null) return;

        double mult = job == Job.HUNTER
                ? cfg().getDouble("merit.hunter-damage-multiplier", 1.25)
                : cfg().getDouble("penalty.combat-damage-multiplier", 0.75);
        e.setDamage(e.getDamage() * mult);
    }

    // ---------- 사냥꾼 : 드롭 ----------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player) return;
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (jobs.getJob(killer.getUniqueId()) != Job.HUNTER) return;

        if (chance(cfg().getDouble("merit.hunter-drop-bonus-chance", 0.4))) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack d : e.getDrops()) {
                // 도구/갑옷 복제 방지: 내구도 있는 아이템은 제외
                if (d.getType().getMaxDurability() == 0) extra.add(d.clone());
            }
            if (!extra.isEmpty()) {
                e.getDrops().addAll(extra);
                bar(killer, "[사냥꾼] 추가 드롭!", NamedTextColor.GREEN);
            }
        }
    }
}
