package com.example.mclinux;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MCLinuxBridge extends JavaPlugin implements CommandExecutor, TabCompleter {

    private Process activeProcess = null;

    @Override
    public void onEnable() {
        if (this.getCommand("mclinux") != null) {
            this.getCommand("mclinux").setExecutor(this);
            this.getCommand("mclinux").setTabCompleter(this);
        }
        saveDefaultConfig();
        setupEmbeddedFastfetch();
        getLogger().info("MCLinuxBridge loaded!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.isOp() && !player.hasPermission("mclinux.admin")) {
                player.sendMessage(ChatColor.RED + "Access Denied! You must be OP or have the mclinux.admin permission.");
                return true;
            }
        }

        if (args == null || args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /mclinux <command>");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            sender.sendMessage(ChatColor.GOLD + "=== MCLinuxBridge Help ===");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux help " + ChatColor.WHITE + "- Shows this guide.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux fastfetch " + ChatColor.WHITE + "- Runs fastfetch specs.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux list [path] " + ChatColor.WHITE + "- Lists a folder.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux cat <file> " + ChatColor.WHITE + "- Reads a file.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux disk " + ChatColor.WHITE + "- Shows storage info.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux reload " + ChatColor.WHITE + "- Reloads config.yml.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux cancel " + ChatColor.WHITE + "- Kills active process.");
            sender.sendMessage(ChatColor.YELLOW + "/mclinux <command> " + ChatColor.WHITE + "- Runs a shell command.");
            return true;
        }

        if (sub.equals("list") || sub.equals("ls")) {
            File targetDir = (args.length > 1) ? new File(args[1]) : new File(".");
            File[] contents = targetDir.listFiles();
            sender.sendMessage(ChatColor.GOLD + "=== Folder: " + targetDir.getAbsolutePath() + " ===");
            if (contents != null && contents.length > 0) {
                for (File f : contents) {
                    String type = f.isDirectory() ? ChatColor.BLUE + "[DIR] " : ChatColor.GREEN + "[FILE] ";
                    sender.sendMessage(type + ChatColor.RESET + f.getName() + ChatColor.GRAY + " (" + (f.length() / 1024) + " KB)");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Directory empty or unreadable.");
            }
            return true;
        }

        if (sub.equals("cat") || sub.equals("read")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /mclinux cat <filename>");
                return true;
            }
            File targetFile = new File(args[1]);
            if (!targetFile.exists() || targetFile.isDirectory()) {
                sender.sendMessage(ChatColor.RED + "File not found!");
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "=== File: " + targetFile.getName() + " ===");
            try (BufferedReader br = new BufferedReader(new FileReader(targetFile))) {
                String line;
                int linesRead = 0;
                while ((line = br.readLine()) != null && linesRead < 50) {
                    sender.sendMessage(ChatColor.RESET + line);
                    linesRead++;
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error reading file: " + e.getMessage());
            }
            return true;
        }

        if (sub.equals("disk")) {
            File root = new File(".");
            long totalSpace = root.getTotalSpace() / (1024 * 1024 * 1024);
            long usableSpace = root.getUsableSpace() / (1024 * 1024 * 1024);
            sender.sendMessage(ChatColor.GOLD + "=== Storage Info ===");
            sender.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + totalSpace + " GB");
            sender.sendMessage(ChatColor.GREEN + "Free: " + ChatColor.WHITE + usableSpace + " GB");
            return true;
        }

        if (sub.equals("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
            return true;
        }

        if (sub.equals("cancel")) {
            if (activeProcess != null && activeProcess.isAlive()) {
                activeProcess.destroyForcibly();
                sender.sendMessage(ChatColor.GOLD + "Process killed.");
                activeProcess = null;
            } else {
                sender.sendMessage(ChatColor.RED + "No process running.");
            }
            return true;
        }

        if (!isWhitelisted(sub)) {
            sender.sendMessage(ChatColor.RED + "Command blocked in config.yml!");
            return true;
        }

        String rawCommand = String.join(" ", args);
        if (sub.equals("fastfetch")) {
            File fastfetchBin = new File(getDataFolder(), "bin/fastfetch-linux-amd64/usr/bin/fastfetch");
            if (fastfetchBin.exists()) {
                rawCommand = fastfetchBin.getAbsolutePath();
            } else {
                sender.sendMessage(ChatColor.RED + "Fastfetch binary missing!");
                return true;
            }
        }

        final String finalCommand = rawCommand;
        sender.sendMessage(ChatColor.YELLOW + "Running: " + finalCommand);

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (activeProcess != null && activeProcess.isAlive()) {
                    activeProcess.destroyForcibly();
                }

                String osName = System.getProperty("os.name").toLowerCase();
                String[] commandArray;

                if (osName.contains("win")) {
                    commandArray = new String[]{"cmd.exe", "/c", finalCommand};
                } else if (osName.contains("mac")) {
                    commandArray = new String[]{"zsh", "-c", finalCommand};
                } else {
                    commandArray = new String[]{"/bin/sh", "-c", finalCommand};
                }

                activeProcess = Runtime.getRuntime().exec(commandArray);

                BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(activeProcess.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    sender.sendMessage(ChatColor.GREEN + "[OS] " + ChatColor.RESET + line);
                }
                while ((line = errReader.readLine()) != null) {
                    sender.sendMessage(ChatColor.RED + "[OS Error] " + ChatColor.RESET + line);
                }

                activeProcess.waitFor(30, TimeUnit.SECONDS);
                activeProcess = null;

            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
                activeProcess = null;
            }
        });

        return true;
    }

    private void setupEmbeddedFastfetch() {
        File binDir = new File(getDataFolder(), "bin");
        File targetTar = new File(binDir, "fastfetch.tar.gz");
        File fastfetchBin = new File(binDir, "fastfetch-linux-amd64/usr/bin/fastfetch");

        if (fastfetchBin.exists()) return;

        try {
            if (!binDir.exists()) {
                binDir.mkdirs();
            }
            InputStream in = getResource("fastfetch.tar.gz");
            if (in == null) return;

            FileOutputStream out = new FileOutputStream(targetTar);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            in.close();
            out.close();

            Process p = Runtime.getRuntime().exec(new String[]{"tar", "-xzf", targetTar.getAbsolutePath(), "-C", binDir.getAbsolutePath()});
            p.waitFor();
            targetTar.delete();

            getLogger().info("Fastfetch extracted successfully!");
        } catch (Exception e) {
            getLogger().warning("Could not extract fastfetch: " + e.getMessage());
        }
    }

    private boolean isWhitelisted(String command) {
        if (!getConfig().getBoolean("whitelist-enabled", true)) return true;
        List<String> allowed = getConfig().getStringList("whitelisted-commands");
        for (String str : allowed) {
            if (str.equalsIgnoreCase(command)) return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args != null && args.length == 1) {
            List<String> base = Arrays.asList("help", "fastfetch", "list", "cat", "disk", "reload", "cancel", "free", "df", "uname");
            List<String> completions = new ArrayList<>();
            String typed = args[0].toLowerCase();
            for (String s : base) {
                if (s.toLowerCase().startsWith(typed)) {
                    completions.add(s);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}