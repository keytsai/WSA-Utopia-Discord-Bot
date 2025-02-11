package tw.waterballsa.utopia.jda

import ch.qos.logback.core.util.OptionHelper.getEnv
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.reflections.Reflections
import org.reflections.scanners.Scanners.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tw.waterballsa.utopia.jda.JdaInstance.compositeListener
import java.lang.reflect.Method

val log = KotlinLogging.logger {}

internal class CompositeListener : EventListener {
    internal val listeners: MutableList<UtopiaListener> = mutableListOf()

    // FIXME: after all deprecated listeners are upgraded to class-oriented listener, should remove all the coupling to DeprecatedUtopiaListener
    internal val deprecatedListeners: MutableList<DeprecatedUtopiaListener> = mutableListOf()

    override fun onEvent(event: GenericEvent) {
        for (listener in listeners) {
            listener.onEvent(event)
        }
        for (listener in deprecatedListeners) {
            listener.onEvent(event)
        }
    }

    fun register(e: UtopiaListener) {
        log.debug { "[Register UtopiaListener] {\"class\":\"${e.javaClass.canonicalName}\"}" }
        listeners.add(e)
    }

    fun register(e: DeprecatedUtopiaListener) {
        log.debug { "[Register UtopiaListener] {\"class\":\"${e.javaClass.canonicalName}\"}" }
        deprecatedListeners.add(e)
    }

}

private object JdaInstance {
    val compositeListener = CompositeListener()
    val instance: JDA by lazy {
        val env = getEnv("BOT_TOKEN").trim()
        val builder = JDABuilder.createDefault(env)
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.DIRECT_MESSAGE_REACTIONS,
                        GatewayIntent.SCHEDULED_EVENTS,
                )
                .enableCache(CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(compositeListener)
        builder.build()
    }
}

@Configuration
open class JdaConfig {
    @Bean
    open fun jda(): JDA {
        return JdaInstance.instance
    }
}

abstract class UtopiaListener : ListenerAdapter() {
    open fun commands(): List<CommandData> {
        return emptyList()
    }
}

@Deprecated("Please use 'UtopiaListener' instead")
open class DeprecatedUtopiaListener : EventListener {
    var name: String? = null
    val listenerDeclarations: MutableMap<Class<out GenericEvent>, (GenericEvent.() -> Unit)> = mutableMapOf()
    val commands = mutableListOf<CommandData>()

    override fun onEvent(e: GenericEvent) {
        listenerDeclarations[e.javaClass]?.invoke(e)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : GenericEvent> on(noinline listenerDeclaration: T.() -> Unit) {
        val pair = T::class.java to listenerDeclaration
        listenerDeclarations[pair.first] = pair.second as GenericEvent.() -> Unit
    }

    fun command(commandDeclaration: () -> CommandData) {
        commands.add(commandDeclaration.invoke())
    }
}

@Deprecated("Use class-oriented approach to declare your listener, see 'tw.waterballsa.utopia.landingx.selfintro.SelfIntroductionListener' for example.")
fun listener(listenerDeclaration: DeprecatedUtopiaListener.() -> Unit): DeprecatedUtopiaListener {
    val listener = DeprecatedUtopiaListener()
    listenerDeclaration.invoke(listener)
    return listener
}

private const val COMPONENT_SCAN_CLASS_PATH = "tw.waterballsa.utopia"

internal fun loadListenerFunctionsFromAllModules(context: ApplicationContext): MutableList<DeprecatedUtopiaListener> {
    val listeners = mutableListOf<DeprecatedUtopiaListener>()
    val reflections = Reflections(COMPONENT_SCAN_CLASS_PATH, SubTypes, TypesAnnotated, MethodsReturn)
    val listenerFunctions = reflections.get(MethodsReturn.with(DeprecatedUtopiaListener::class.java).`as`(Method::class.java))
            .filterNot {
                it.declaringClass.`package`.name.startsWith("tw.waterballsa.utopia.jda")
            }

    for (listenerFunction in listenerFunctions) {
        val parameters = listenerFunction.parameters
        val arguments = arrayOfNulls<Any>(parameters.size)

        parameters.forEachIndexed { i, p ->
            arguments[i] = if (p.isAnnotationPresent(Qualifier::class.java)) {
                val qualifier = p.getAnnotation(Qualifier::class.java)
                val beanName = qualifier.value
                context.getBean(beanName)
            } else {
                context.getBean(p.type)
            }
        }
        val listener = listenerFunction.invoke(null, *arguments) as DeprecatedUtopiaListener
        listener.name = listenerFunction.name
        listeners.add(listener)
    }
    return listeners
}

const val WSA_GUILD_BEAN_NAME = "WsaGuild"

fun runJda() {
    JdaInstance.instance.awaitReady()
}

fun registerAllJdaListeners(context: AnnotationConfigApplicationContext) {
    val wsa = context.getBean(WSA_GUILD_BEAN_NAME, Guild::class.java)

    val listenerFunctions = loadListenerFunctionsFromAllModules(context)

    listenerFunctions.onEach { compositeListener.register(it) }
            .flatMap { it.commands }
            .let { wsa.updateCommands().addCommands(it).queue() }

    val utopiaListeners = context.getBeansOfType(UtopiaListener::class.java).values
    utopiaListeners.forEach { compositeListener.register(it) }

    utopiaListeners.flatMap { it.commands() }.let {
        wsa.updateCommands().addCommands(it).queue()
    }
}
