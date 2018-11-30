package github.scarsz.discordsupportbot;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import github.scarsz.discordsupportbot.command.AdminCommandListener;
import github.scarsz.discordsupportbot.command.dev.DevelopmentCommandListener;
import github.scarsz.discordsupportbot.command.dev.ShutdownHookThread;
import github.scarsz.discordsupportbot.config.ConfigListener;
import github.scarsz.discordsupportbot.config.SetupListener;
import github.scarsz.discordsupportbot.exception.DataInvalidException;
import github.scarsz.discordsupportbot.exception.HelpdeskDoesNotExistException;
import github.scarsz.discordsupportbot.http.HttpServer;
import github.scarsz.discordsupportbot.support.Helpdesk;
import github.scarsz.discordsupportbot.support.Ticket;
import github.scarsz.discordsupportbot.support.TicketVoiceChannel;
import lombok.Getter;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.ErrorResponseException;
import net.dv8tion.jda.core.requests.RestAction;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class SupportBot {

    /*
    //todo:
    - ticket expiration
      - added to config
    - forward subsequent messages to a recently created ticket
    - statistics (low priority, will probably come after v2 is confirmed stable)
     */

    private static SupportBot SUPPORT_BOT;

    @Getter private final Connection database;
    @Getter private final Set<Helpdesk> helpdesks = new HashSet<>();
    @Getter private final JDA jda;
    @Getter private final HttpServer httpServer;
    @Getter private final EventWaiter waiter = new EventWaiter();
    @Getter private final ConfigListener configListener;
    @Getter private final Thread shutdownThread = new ShutdownHookThread();

    public SupportBot(String botToken) throws Exception {
        // set instance
        SupportBot.SUPPORT_BOT = this;

        // connect sqlite db
        Class.forName("org.sqlite.JDBC");
        database = DriverManager.getConnection("jdbc:sqlite:" + new File("support.db"));
        database.setAutoCommit(false);

        // hook shutdown
        try {
            Runtime.getRuntime().addShutdownHook(shutdownThread);
        } catch (Exception e) {
            System.err.println("Failed to add shutdown hook, not starting");
            System.exit(2);
        }

//        Field modifiersField = Field.class.getDeclaredField("modifiers");
//        modifiersField.setAccessible(true);
//        Field slf4J_enabled = JDALogger.class.getDeclaredField("SLF4J_ENABLED");
//        slf4J_enabled.setAccessible(true);
//        modifiersField.setInt(slf4J_enabled, slf4J_enabled.getModifiers() & ~Modifier.FINAL);
//        slf4J_enabled.set(null, false);

        jda = new JDABuilder(AccountType.BOT)
                .setAudioEnabled(false)
                .setBulkDeleteSplittingEnabled(false)
                .setToken(botToken)
//                .setGame(Game.playing("support.scarsz.me"))
                .setGame(Game.playing("under development, frequently restarting"))
                .addEventListener(waiter)
                .addEventListener(configListener = new ConfigListener())
                .build().awaitStatus(JDA.Status.CONNECTED);
        jda.addEventListener(new SetupListener());
        jda.addEventListener(new AdminCommandListener());
        jda.addEventListener(new DevelopmentCommandListener());

        List<Integer> ignoredErrors = Arrays.asList(10003, 10008);
        RestAction.DEFAULT_FAILURE = throwable -> {
            if (throwable instanceof ErrorResponseException && ignoredErrors.contains(((ErrorResponseException) throwable).getErrorCode())) return;
            if (throwable.getCause() != null) {
                RestAction.LOG.error("RestAction queue returned failure: [{}] {}", throwable.getClass().getSimpleName(), throwable.getMessage(), throwable.getCause());
            } else {
                RestAction.LOG.error("RestAction queue returned failure: [{}] {}", throwable.getClass().getSimpleName(), throwable.getMessage());
            }
        };

//        TextChannel channel = jda.getTextChannelById("382266272877903885");
//        MessageHistory history = channel.getHistory();
//        int banned = 0;
//        do {
//            for (Message message : history.retrievePast(100).complete()) {
//                if (message.getContentRaw().contains("We are Anonymous")) {
//                    message.getGuild().getController().ban(message.getAuthor(), 7).complete();
//                    banned++;
//                } else {
//                    history = null;
//                }
//            }
//        } while (history != null);
//        channel.sendMessage("banned " + banned).queue();

        // toasty
//        Helpdesk helpdesk = new Helpdesk(
//                jda.getCategoryById("469775207884914688"),
//                jda.getTextChannelById("469775260909043712")
//        );
//        helpdesk.getPrompts().add(new FreeResponsePrompt(helpdesk.getUuid(), UUID.randomUUID(), "Server", "Which server is the issue on?"));
//        helpdesk.getPrompts().add(new FreeResponsePrompt(helpdesk.getUuid(), UUID.randomUUID(), "Username", "What's your in-game name?"));
//        helpdesks.add(helpdesk);
//        database.close();

        PreparedStatement statement = database.prepareStatement("SELECT `uuid` FROM `helpdesks`");
        ResultSet result = statement.executeQuery();
        while (result.next()) {
            try {
                helpdesks.add(new Helpdesk(UUID.fromString(result.getString("uuid"))));
            } catch (DataInvalidException ignored) {
            } catch (HelpdeskDoesNotExistException e) {
                e.printStackTrace();
            }
        }
        System.out.println(helpdesks.size() + " helpdesks loaded");

        System.out.println(jda.getGuilds().size() + " guilds " + jda.getGuilds());
        Set<Guild> guildsNoRep = jda.getGuilds().stream()
                .filter(guild -> {
                    Set<User> membersWithAdmin = guild.getMembers().stream().filter(member -> member.hasPermission(Permission.ADMINISTRATOR)).map(Member::getUser).collect(Collectors.toSet());
                    return jda.getGuildById("382266272450215937").getMembers().stream().noneMatch(member -> membersWithAdmin.contains(member.getUser()));
                })
                .collect(Collectors.toSet());
        System.out.println(guildsNoRep.size() + " guilds without representatives " + guildsNoRep);
//        jda.getGuilds().stream().map(guild -> guild.getMembers().size() + " " + guild.getName() + " " + guild.getId()).sorted().forEach(System.out::println);

        this.httpServer = new HttpServer();
    }

    private void flush() {
        try {
            System.out.print("Saving " + helpdesks.size() + " helpdesks");
            helpdesks.forEach(helpdesk -> {
                helpdesk.flush();
                System.out.print(".");
            });
            System.out.println();
            System.out.println("Committing to database");
            database.commit();
            database.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        // write all data to database
        flush();

        // kill voice channels
        for (Helpdesk helpdesk : helpdesks) {
            for (Ticket ticket : helpdesk.getTickets()) {
                TicketVoiceChannel voiceChannel = ticket.getVoiceChannel();
                if (voiceChannel != null) {
                    voiceChannel.destroy();
                }
            }
        }

        System.out.println("Waiting for JDA to shutdown...");
        jda.shutdown();
        while (jda.getStatus() != JDA.Status.SHUTDOWN) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Closing database connection...");
        try {
            SupportBot.get().getDatabase().close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Goodbye");
        Runtime.getRuntime().halt(0);
    }

    public Helpdesk getHelpdesk(UUID uuid) {
        return helpdesks.stream()
                .filter(helpdesk -> helpdesk.getUuid().equals(uuid))
                .findFirst().orElse(null);
    }
    public Helpdesk getHelpdeskForCategory(String categoryId) {
        return helpdesks.stream()
                .filter(helpdesk -> helpdesk.getCategory().getId().equals(categoryId))
                .findFirst().orElse(null);
    }
    public Helpdesk getHelpdeskForCategory(Category category) {
        return getHelpdeskForCategory(category.getId());
    }
    public Helpdesk getHelpdeskForTicket(UUID uuid) {
        return helpdesks.stream()
                .filter(helpdesk -> helpdesk.getTickets().stream().anyMatch(ticket -> ticket.getUuid().equals(uuid)))
                .findFirst().orElse(null);
    }
    public Helpdesk getHelpdeskForTicket(Ticket ticket) {
        return getHelpdeskForTicket(ticket.getUuid());
    }
    public Set<Helpdesk> getHelpdesksForGuild(String guildId) {
        return getHelpdesksForGuild(jda.getGuildById(guildId));
    }
    public Set<Helpdesk> getHelpdesksForGuild(Guild guild) {
        if (guild == null) return Collections.emptySet();
        return helpdesks.stream()
                .filter(helpdesk -> helpdesk.getStartingChannel() != null)
                .filter(helpdesk -> helpdesk.getStartingChannel().getGuild().equals(guild))
                .collect(Collectors.toSet());
    }

    public static SupportBot get() {
        return SUPPORT_BOT;
    }

}
