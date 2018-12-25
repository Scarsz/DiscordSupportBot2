package github.scarsz.discordsupportbot.config;

import github.scarsz.discordsupportbot.SupportBot;
import github.scarsz.discordsupportbot.prompt.FreeResponsePrompt;
import github.scarsz.discordsupportbot.prompt.Prompt;
import github.scarsz.discordsupportbot.support.Helpdesk;
import github.scarsz.discordsupportbot.util.ChoiceUtil;
import github.scarsz.discordsupportbot.util.Emoji;
import github.scarsz.discordsupportbot.util.FooterUtil;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.exceptions.ErrorResponseException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConfigurationMessage extends ListenerAdapter {

    @Getter private final ConfigListener listener;
    @Getter private final Message message;
    @Getter private final Member member;
    @Getter private final Guild guild;

    private Helpdesk selection = null;
    private boolean dead = false;

    public ConfigurationMessage(ConfigListener listener, Message message, Member member) {
        this.listener = listener;
        this.message = message;
        this.member = member;
        this.guild = member.getGuild();

        setState(State.SELECTING);
        SupportBot.get().getJda().addEventListener(this);
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        if (event.getMessageId().equals(message.getId())) {
            dead = true;
            listener.getConfigurationMessages().remove(this);
            SupportBot.get().getJda().removeEventListener(this);
        }
    }

    public void setState(ConfigurationMessage.State state) {
        // configuration set state
//        System.out.println("Set state: " + state.name() + "\n" + Arrays.stream(ExceptionUtils.getStackFrames(new Throwable())).filter(s -> {
//            return s.contains("ConfigurationMessage") && s.contains("github.scarsz");
//        }).skip(1).collect(Collectors.joining("\n")));

//        List<String> keep = Arrays.asList(Emoji.HEAVY_PLUS_SIGN, Emoji.HEAVY_MINUS_SIGN, Emoji.BABY, Emoji.SCROLL, Emoji.FILE_FOLDER, Emoji.X, Emoji.BACK, Emoji.WHITE_CHECK_MARK);
//        for (MessageReaction reaction : message.getReactions()) {
//            if (!keep.contains(reaction.getReactionEmote().getName())) {
//                for (User user : reaction.getUsers().complete()) {
//                    reaction.removeReaction(user).queue();
//                }
//                continue;
//            }
//            reaction.getUsers().queue(users -> {
//                for (User user : users) {
//                    if (user.equals(SupportBot.get().getJda().getSelfUser())) continue;
//                    reaction.removeReaction(user).queue();
//                }
//            });
//        }
        try {
            message.clearReactions().queue();
        } catch (ErrorResponseException e) {
            dead = true;
        }

        if (dead) return;

        switch (state) {
            case SELECTING:
                Set<Helpdesk> helpdesks = SupportBot.get().getHelpdesksForGuild(message.getGuild());
                if (helpdesks.size() == 0) {
                    message.editMessage(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("No helpdesks to configure")
                            .setDescription("Configuration session aborting.")
                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                            .build()
                    ).queue(v -> destroy());
                    return;
                }
                if (helpdesks.size() == 1) {
                    selection = helpdesks.iterator().next();
                    message.getTextChannel().sendMessage(new EmbedBuilder()
                            .setColor(Color.GRAY)
                            .setTitle("Only one helpdesk exists, automatically selecting `" + selection.getCategory().getName() + " / " + selection.getStartingChannel().getName() + "`")
                            .setFooter("To create another helpdesk, use `@" + message.getGuild().getSelfMember().getEffectiveName() + " setup/autosetup`", "https://upload.wikimedia.org/wikipedia/en/thumb/3/35/Information_icon.svg/1024px-Information_icon.svg.png")
                            .build()
                    ).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                    setState(State.MAIN_MENU);
                    return;
                }

                EmbedBuilder builder = new EmbedBuilder()
                        .setColor(Color.WHITE)
                        .setTitle("Choose which helpdesk you want to configure")
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl());
                helpdesks.forEach(helpdesk -> {
                    String emoji = ChoiceUtil.number(builder.getFields().size() + 1);
                    message.addReaction(emoji).queue();
                    builder.addField(emoji, helpdesk.getCategory().getName() + " / " + helpdesk.getStartingChannel().getName(), false);
                });
                message.editMessage(builder.build()).queue();

                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReactionAddEvent.class,
                        event -> {
                            int choice = ChoiceUtil.decimal(event.getReactionEmote().getName());
                            boolean valid = choice != 0 && helpdesks.stream().skip(choice - 1).findFirst().orElse(null) != null;
                            return valid && event.getMember() != null && event.getMember().equals(member) && event.getMessageId().equals(message.getId()) && ChoiceUtil.isValidChoice(event.getReactionEmote().getName());
                        },
                        event -> {
                            int choice = ChoiceUtil.decimal(event.getReactionEmote().getName());
                            Helpdesk helpdesk = helpdesks.stream().skip(choice - 1).findFirst().orElse(null);
                            if (helpdesk == null) return;
                            selection = helpdesk;
                            setState(State.MAIN_MENU);
                        },
                        1, TimeUnit.MINUTES,
                        () -> message.editMessage(new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("Configuration session expired")
                                .setDescription("No further commands were received.")
                                .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                .build()
                        ).queue(v -> destroy())
                );
                break;
            case MAIN_MENU:
                message.editMessage(new EmbedBuilder()
                        .setColor(Color.WHITE)
                        .setTitle(selection.getCategory().getName() + " / " + selection.getStartingChannel().getName())
                        .setThumbnail(message.getGuild().getIconUrl())
                        .addField(Emoji.HEAVY_PLUS_SIGN, "Add a prompt", true)
                        .addField(Emoji.HEAVY_MINUS_SIGN, "Remove a prompt", true)
                        .addField(Emoji.BABY, "Toggle abandoned ticket closure", true)
                        .addField(Emoji.SCROLL, "Toggle sending transcripts", true)
                        .addField(Emoji.FILE_FOLDER, "Change transcripts logging channel", true)
                        .addField(Emoji.NERD, "Change ticket master role", true)
                        .addField(Emoji.X, "Delete helpdesk", true)
                        .addField(Emoji.BACK, "Back to selection", true)
                        .addField(Emoji.WHITE_CHECK_MARK, "Finished", true)
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue();
                List<String> allEmojis = Arrays.asList(
                        Emoji.HEAVY_PLUS_SIGN,
                        Emoji.HEAVY_MINUS_SIGN,
                        Emoji.BABY,
                        Emoji.SCROLL,
                        Emoji.FILE_FOLDER,
                        Emoji.NERD,
                        Emoji.X,
                        Emoji.BACK,
                        Emoji.WHITE_CHECK_MARK
                );
                for (String emoji : allEmojis) {
                    message.addReaction(emoji).queue();
                }

                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReactionAddEvent.class,
                        event -> event.getMember().equals(member) && event.getMessageId().equals(message.getId()) && allEmojis.contains(event.getReactionEmote().getName()),
                        event -> {
                            String action = event.getReactionEmote().getName();
                            switch (action) {
                                case Emoji.HEAVY_PLUS_SIGN: setState(State.ADD_PROMPT); break;
                                case Emoji.HEAVY_MINUS_SIGN: setState(State.REMOVE_PROMPT); break;
                                case Emoji.BABY: setState(State.TOGGLE_SOLVING_ON_ABANDON); break;
                                case Emoji.SCROLL: setState(State.TOGGLE_TRANSCRIPTS); break;
                                case Emoji.FILE_FOLDER: setState(State.SET_TRANSCRIPT_CHANNEL); break;
                                case Emoji.NERD: setState(State.SET_TICKET_MASTER); break;
                                case Emoji.X: setState(State.DELETE_HELPDESK); break;
                                case Emoji.BACK: setState(State.SELECTING); break;
                                case Emoji.WHITE_CHECK_MARK: {
                                    message.editMessage(new EmbedBuilder()
                                            .setColor(Color.GREEN)
                                            .setTitle("Finished editing helpdesk configuration.")
                                            .setFooter(FooterUtil.make(event.getUser()), event.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(v -> destroy());
                                    break;
                                }
                            }
                        },
                        1, TimeUnit.MINUTES,
                        () -> message.editMessage(new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("Configuration session expired")
                                .setDescription("No further commands were received.")
                                .build()
                        ).queue(v -> destroy())
                );
                break;
            case ADD_PROMPT:
                message.editMessage(new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Please type the title of the prompt")
                        .setDescription("This is used in the leading message for the title of the prompt's answer. Try to keep this short.")
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(message -> {
                    SupportBot.get().getWaiter().waitForEvent(
                            GuildMessageReceivedEvent.class,
                            event -> event.getMember() != null && event.getMember().equals(member) && event.getChannel().equals(message.getTextChannel()),
                            event -> {
                                String promptTitleTemp = event.getMessage().getContentRaw();
                                promptTitleTemp = promptTitleTemp.substring(0, 1).toUpperCase() + promptTitleTemp.substring(1);
                                String promptTitle = promptTitleTemp;

                                message.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.YELLOW)
                                        .setTitle("Please type the description of the prompt")
                                        .setDescription("This is the actual message that is sent to the user when they try making a ticket.")
                                        .build()
                                ).queue(message1 -> {
                                    SupportBot.get().getWaiter().waitForEvent(
                                            GuildMessageReceivedEvent.class,
                                            event2 -> {
                                                boolean lengthOkay = event2.getMessage().getContentRaw().length() <= 1024;
                                                if (!lengthOkay) {
                                                    message.getTextChannel().sendMessage(new EmbedBuilder().setColor(Color.RED).setTitle("Maximum length is 1024 characters. Try again.").build()).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                                                    return false;
                                                }

                                                return event2.getMember() != null && event2.getMember().equals(member) && event2.getChannel().equals(message.getTextChannel());
                                            },
                                            event2 -> {
                                                String promptDescription = event2.getMessage().getContentRaw();
                                                promptDescription = promptDescription.substring(0, 1).toUpperCase() + promptDescription.substring(1);
                                                if (Character.isLetter(promptDescription.length() - 1)) promptDescription += "?";
                                                selection.getPrompts().add(new FreeResponsePrompt(selection.getUuid(), UUID.randomUUID(), promptTitle, promptDescription));
                                                message.getChannel().sendMessage(new EmbedBuilder()
                                                        .setColor(Color.GREEN)
                                                        .setTitle("Successfully created new free response prompt")
                                                        .addField("Title", promptTitle, true)
                                                        .addField("Message", promptDescription, true)
                                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                                        .build()
                                                ).queue(message2 -> message2.delete().queueAfter(5, TimeUnit.SECONDS));
                                                event.getMessage().delete().queue();
                                                event2.getMessage().delete().queue();
                                                message1.delete().queue();
                                                setState(State.MAIN_MENU);
                                            },
                                            1, TimeUnit.MINUTES,
                                            () -> {
                                                message.getChannel().sendMessage(new EmbedBuilder()
                                                        .setColor(Color.RED)
                                                        .setTitle("Prompt creation timed out")
                                                        .setDescription("No description for the new prompt was given")
                                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                                        .build()
                                                ).queue(message2 -> message2.delete().queueAfter(5, TimeUnit.SECONDS));
                                                event.getMessage().delete().queue();
                                                message1.delete().queue();
                                                setState(State.MAIN_MENU);
                                            }
                                    );
                                });
                            },
                            1, TimeUnit.MINUTES,
                            () -> {
                                message.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Prompt creation timed out")
                                        .setDescription("No title for the new prompt was given")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                setState(State.MAIN_MENU);
                            }
                    );
                });
                break;
            case REMOVE_PROMPT:
                if (selection.getPrompts().size() == 0) {
                    message.getChannel().sendMessage(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("This helpdesk has no prompts to remove")
                            .build()
                    ).queue(message1 -> {
                        message1.delete().queueAfter(5, TimeUnit.SECONDS);
                        setState(State.MAIN_MENU);
                    });
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(Color.WHITE)
                        .setTitle("Choose which prompt you'd like to delete")
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl());
                selection.getPrompts().forEach(prompt -> {
                    String emoji = ChoiceUtil.number(embed.getFields().size() + 1);
                    message.addReaction(emoji).queue();
                    embed.addField(emoji, prompt.getName(), false);
                });
                message.editMessage(embed.build()).queue(message -> {
                    SupportBot.get().getWaiter().waitForEvent(
                            GuildMessageReactionAddEvent.class,
                            event -> event.getMember() != null && event.getMember().equals(member) && event.getMessageId().equals(message.getId()) && ChoiceUtil.isValidChoice(event.getReactionEmote().getName()),
                            event -> {
                                int choice = ChoiceUtil.decimal(event.getReactionEmote().getName());
                                Prompt prompt = selection.getPrompts().get(choice - 1);
                                if (prompt != null) {
                                    prompt.destroy();
                                    message.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.GREEN)
                                            .setTitle("Successfully removed prompt")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                } else {
                                    message.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.RED)
                                            .setTitle("Prompt removal aborted")
                                            .setDescription("Response was not valid")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                }
                                setState(State.MAIN_MENU);
                            },
                            1, TimeUnit.MINUTES,
                            () -> message.editMessage(new EmbedBuilder()
                                    .setColor(Color.RED)
                                    .setTitle("Prompt deletion timed out")
                                    .setDescription("No prompt was selected for removal.")
                                    .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                    .build()
                            ).queue(v -> setState(State.MAIN_MENU))
                    );
                });
                break;
            case TOGGLE_SOLVING_ON_ABANDON:
                selection.getConfig().setMarkAsSolvedOnAbandon(!selection.getConfig().shouldMarkAsSolvedOnAbandon());
                message.getChannel().sendMessage(new EmbedBuilder()
                        .setColor(selection.getConfig().shouldMarkAsSolvedOnAbandon() ? Color.GREEN : Color.RED)
                        .setTitle("Configuration changed")
                        .setDescription("Marking tickets as solved when the author has left is now " + (selection.getConfig().shouldMarkAsSolvedOnAbandon() ? "ON" : "OFF"))
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
                setState(State.MAIN_MENU);
                break;
            case TOGGLE_TRANSCRIPTS:
                selection.getConfig().setSendTranscripts(!selection.getConfig().shouldSendTranscripts());
                message.getChannel().sendMessage(new EmbedBuilder()
                        .setColor(selection.getConfig().shouldSendTranscripts() ? Color.GREEN : Color.RED)
                        .setTitle("Configuration changed")
                        .setDescription("Sending participants the ticket transcript on closure is now " + (selection.getConfig().shouldSendTranscripts() ? "ON" : "OFF"))
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(message -> message.delete().queueAfter(5, TimeUnit.SECONDS));
                setState(State.MAIN_MENU);
                break;
            case SET_TRANSCRIPT_CHANNEL:
                message.editMessage(new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("#mention which channel should be used to log tickets")
                        .setDescription("If you do not wish to have a ticket logging channel, respond with a message containing no channel #mentions.")
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(msg -> {
                    SupportBot.get().getWaiter().waitForEvent(
                            GuildMessageReceivedEvent.class,
                            event -> event.getMember() != null && event.getMember().equals(member) && event.getChannel().equals(msg.getTextChannel()),
                            event -> {
                                List<TextChannel> mentionedChannels = event.getMessage().getMentionedChannels();
                                if (mentionedChannels.size() == 0) {
                                    selection.getConfig().setTranscriptLogChannel(null);
                                    msg.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.RED)
                                            .setTitle("Configuration changed")
                                            .setDescription("No text channel was mentioned thus sending transcripts to a log channel has been disabled.")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                } else {
                                    TextChannel channel = mentionedChannels.get(0);
                                    selection.getConfig().setTranscriptLogChannel(channel.getId());
                                    msg.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.GREEN)
                                            .setTitle("Configuration changed")
                                            .setDescription("Ticket transcripts will now be sent to " + channel.getAsMention() + ".")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                }
                                event.getMessage().delete().queueAfter(5, TimeUnit.SECONDS);
                                setState(State.MAIN_MENU);
                            },
                            1, TimeUnit.MINUTES,
                            () -> {
                                msg.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Configuration change timed out")
                                        .setDescription("No channel was given")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                setState(State.MAIN_MENU);
                            }
                    );
                });
                break;
            case SET_TICKET_MASTER:
                String roles = guild.getRoles().stream()
                        .filter(role -> !role.isManaged() && !role.isPublicRole())
                        .map(Role::getName)
                        .collect(Collectors.joining("`, `", "`", "`"));
                if (roles.length() >= 1024) roles = roles.substring(0, 1021) + "...";

                message.editMessage(new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Specify which role should be able to provide support (currently `" + (selection.getConfig().getTicketMasterRole() != null ? selection.getConfig().getTicketMasterRole().getName() : "none") + "`)")
                        .setDescription("If you do not wish to change the ticket master role, respond with a message containing no role names.")
                        .addField("Available roles", roles, false)
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue(msg -> {
                    SupportBot.get().getWaiter().waitForEvent(
                            GuildMessageReceivedEvent.class,
                            event -> {
                                boolean correctUser = event.getMember() != null && event.getMember().equals(member) && event.getChannel().equals(msg.getTextChannel());
                                if (!correctUser) return false;
                                List<Role> matchedRoles = StringUtils.isBlank(event.getMessage().getContentRaw()) ? new ArrayList<>() : guild.getRolesByName(event.getMessage().getContentRaw().trim(), true);
                                if (matchedRoles.size() == 0) {
                                    msg.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.RED)
                                            .setTitle("No roles matched- try again.")
                                            .setDescription("No valid role was given. You must say the role's exact name, without @mentioning it.")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                                } else if (matchedRoles.size() >= 2) {
                                    msg.getChannel().sendMessage(new EmbedBuilder()
                                            .setColor(Color.RED)
                                            .setTitle("Multiple roles matched- try again.")
                                            .setDescription("Given role name matched multiple roles. There can only be one match.")
                                            .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                            .build()
                                    ).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                                }
                                return matchedRoles.size() == 1;
                            },
                            event -> {
                                Role matchedRole = guild.getRolesByName(event.getMessage().getContentRaw().trim(), true).get(0);
                                selection.getConfig().setTicketMasterRole(matchedRole);
                                msg.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.GREEN)
                                        .setTitle("Configuration changed")
                                        .setDescription("Tickets can now be responded to and closed by users with the `" + matchedRole.getName() + "` role.")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                                event.getMessage().delete().queueAfter(5, TimeUnit.SECONDS);
                                setState(State.MAIN_MENU);
                            },
                            1, TimeUnit.MINUTES,
                            () -> {
                                msg.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Configuration change timed out")
                                        .setDescription("No valid role was given")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                                setState(State.MAIN_MENU);
                            }
                    );
                });
                break;
            case DELETE_HELPDESK:
                message.editMessage(new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle("Are you sure you want to delete this helpdesk?")
                        .setDescription("This action is not undoable.")
                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                        .build()
                ).queue();
                message.addReaction(Emoji.WHITE_CHECK_MARK).queue();
                message.addReaction(Emoji.X).queue();
                SupportBot.get().getWaiter().waitForEvent(
                        GuildMessageReactionAddEvent.class,
                        event -> event.getMember().equals(member) && Arrays.asList(Emoji.WHITE_CHECK_MARK, Emoji.X).contains(event.getReactionEmote().getName()) && event.getMessageId().equals(message.getId()),
                        event -> {
                            boolean confirmation = event.getReactionEmote().getName().equals(Emoji.WHITE_CHECK_MARK);
                            if (confirmation) {
                                selection.destroy();
                                message.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.GREEN)
                                        .setTitle("Configuration changed")
                                        .setDescription("Helpdesk `" + selection.getCategory().getName() + " / " + selection.getStartingChannel().getName() + "` was deleted.")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                setState(State.SELECTING);
                            } else {
                                message.getChannel().sendMessage(new EmbedBuilder()
                                        .setColor(Color.RED)
                                        .setTitle("Configuration unchanged")
                                        .setDescription("Helpdesk `" + selection.getCategory().getName() + " / " + selection.getStartingChannel().getName() + "` was NOT deleted.")
                                        .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                        .build()
                                ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                                setState(State.MAIN_MENU);
                            }
                        },
                        1, TimeUnit.MINUTES,
                        () -> {
                            message.getChannel().sendMessage(new EmbedBuilder()
                                    .setColor(Color.RED)
                                    .setTitle("Configuration change timed out")
                                    .setDescription("No confirmation was given for helpdesk closure.")
                                    .setFooter(FooterUtil.make(member), member.getUser().getEffectiveAvatarUrl())
                                    .build()
                            ).queue(message1 -> message1.delete().queueAfter(5, TimeUnit.SECONDS));
                            setState(State.MAIN_MENU);
                        }
                );
                selection.destroy();
                break;
        }
    }

    private void destroy() {
        message.clearReactions().queue();
        message.delete().queueAfter(5, TimeUnit.SECONDS, v -> listener.getConfigurationMessages().remove(this));
    }

    public enum State {
        SELECTING,
        MAIN_MENU,
        ADD_PROMPT,
        REMOVE_PROMPT,
        TOGGLE_SOLVING_ON_ABANDON,
        TOGGLE_TRANSCRIPTS,
        SET_TRANSCRIPT_CHANNEL,
        SET_TICKET_MASTER,
        DELETE_HELPDESK
    }

}
