package tw.waterballsa.utopia.gaas

import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.springframework.stereotype.Component
import tw.waterballsa.utopia.jda.UtopiaListener

internal const val OPTION_EVENT_DATE_YEAR = "event-date-year"
internal const val OPTION_EVENT_DATE_MONTH = "event-date-month"
internal const val OPTION_EVENT_DATE_DAY = "event-date-day"
internal const val OPTION_MEMBER = "gaas-member"

@Component
class CommandRegister : UtopiaListener() {
    override fun commands(): List<CommandData> {
        return listOf(
            Commands.slash("gaas", "GaaS Command")
                .addSubcommands(
                    SubcommandData("stats-avg-and-max", "Get Avg And Max Participants Number at Specific Date")
                        .addRequiredOption(OptionType.INTEGER, OPTION_EVENT_DATE_YEAR, "Year")
                        .addRequiredOption(OptionType.INTEGER, OPTION_EVENT_DATE_MONTH, "Month")
                        .addRequiredOption(OptionType.INTEGER, OPTION_EVENT_DATE_DAY, "Day")
                )
                .addSubcommands(
                    SubcommandData("observe", "Add a specific member to the watchlist")
                        .addRequiredOption(OptionType.USER, OPTION_MEMBER, "GaaS Member")
                )
                .addSubcommands(
                    SubcommandData("unobserved", "Remove a specific member from the watchlist")
                        .addRequiredOption(OptionType.USER, OPTION_MEMBER, "GaaS Member")
                )
        )
    }
}
