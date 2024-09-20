package com.simiacryptus.skyenet.apps.premium

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ImageModels
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.models.OpenAITextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.util.JsonUtil.toJson
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.premium.PresentationDesignerActors.*
import com.simiacryptus.skyenet.core.actors.*
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.StorageInterface
import com.simiacryptus.skyenet.core.platform.User
import com.simiacryptus.skyenet.core.util.StringSplitter
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.util.MarkdownUtil.renderMarkdown
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory


open class PresentationDesignerApp(
    applicationName: String = "Presentation Generator v1.4",
) : ApplicationServer(
    applicationName = applicationName,
    path = "/presentation",
) {

    override val description: String
        @Language("HTML")
        get() = "<div>" + renderMarkdown(
            """
        Welcome to the Presentation Designer, an app designed to help you create presentations with ease.
        
        Enter a prompt, and the Presentation Designer will generate a presentation for you, complete with slides, images, and speaking notes!                  
      """.trimIndent()
        ) + "</div>"

    data class Settings(
        val model: OpenAITextModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.1,
        val voice: String? = "alloy",
        val voiceSpeed: Double? = 1.0,
        val budget: Double = 2.0,
    )

    var openAI = OpenAIClient()

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        try {
            val settings = getSettings<Settings>(session, user)
            PresentationDesignerAgent(
                user = user,
                session = session,
                dataStorage = dataStorage,
                ui = ui,
                chat = api,
                openAI = openAI,
                model = settings?.model ?: OpenAIModels.GPT4oMini,
                temperature = settings?.temperature ?: 0.3,
                voice = settings?.voice ?: "alloy",
                voiceSpeed = settings?.voiceSpeed ?: 1.0,
            ).main(userMessage)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PresentationDesignerApp::class.java)
    }

}


open class PresentationDesignerAgent(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val chat: API,
    val openAI: OpenAIClient,
    model: OpenAITextModel = OpenAIModels.GPT4oMini,
    temperature: Double = 0.3,
    val voice: String = "alloy",
    val voiceSpeed: Double = 1.0,
) : ActorSystem<ActorType>(
    PresentationDesignerActors(
        model = model,
        temperature = temperature,
        voice = voice,
        voiceSpeed = voiceSpeed,
    ).actorMap.map { it.key.name to it.value }.toMap(), dataStorage, user, session
) {

    @Suppress("UNCHECKED_CAST")
    private val initialAuthor by lazy { getActor(ActorType.INITIAL_AUTHOR) as ParsedActor<Outline> }
    private val slideAuthor by lazy { getActor(ActorType.CONTENT_EXPANDER) as SimpleActor }
    private val slideLayout by lazy { getActor(ActorType.SLIDE_LAYOUT) as SimpleActor }
    private val slideSummary by lazy { getActor(ActorType.SLIDE_SUMMARY) as SimpleActor }
    private val imageRenderer by lazy { getActor(ActorType.IMAGE_RENDERER) as ImageActor }
    private val narrator get() = getActor(ActorType.NARRATOR) as TextToSpeechActor

    val tabs = TabbedDisplay(ui.newTask())

    fun main(userRequest: String) {
        val mainTask = ui.newTask(false).apply { tabs["Main"] = placeholder }
        try {
            mainTask.echo(userRequest)
            mainTask.header("Starting Presentation Generation")

            // Step 1: Generate ideas based on the user's request
            val ideaListResponse = initialAuthor.answer(listOf(userRequest), api = chat)
            mainTask.add(renderMarkdown(ideaListResponse.text, ui = ui))
            mainTask.verbose(toJson(ideaListResponse.obj))

            // Step 3: Style the slides and generate speaking notes
            val slideTabs = TabbedDisplay(mainTask)
            val styledSlidesAndNotes = ideaListResponse.obj.slides?.withIndex()?.map { (idx, slide) ->
                val slideTask = ui.newTask(false).apply { slideTabs[idx.toString()] = placeholder }
                slideTask.header("Generating slide $idx: ${slide.title}")
                pool.submit<SlideContents> {
                    slideContents(userRequest, slide, slideTask, idx, ideaListResponse.text)
                }
            }?.mapNotNull { it.get() } ?: emptyList()

            // Step 4: Present the results
            writeReports(styledSlidesAndNotes, ui.newTask(false).apply { tabs["Output"] = placeholder })

            mainTask.complete("Presentation generation complete.")
        } catch (e: Throwable) {
            mainTask.error(ui, e)
            throw e
        }
    }

    private fun slideContents(
        userRequest: String,
        slide: SlideInfo,
        slideTask: SessionTask,
        idx: Int,
        text: String
    ) = try {
        val slideTabs = TabbedDisplay(slideTask)
        var slideTask = slideTask

        val content = slideAuthor.answer(
            listOf(
                userRequest,
                text,
                """
                |Expand ${slide.title}
                |
                |${slide.content}
                """.trimMargin()
            ), api = chat
        )
        slideTask = ui.newTask(false).apply { slideTabs["Details"] = placeholder }
        slideTask.add(renderMarkdown(content, ui = ui))
        val list = listOf(content)
        val image = imageRenderer.setImageAPI(
            ApplicationServices.clientManager.getOpenAIClient(session, user)
        ).answer(list, api = chat).image
        val imageStr = slideTask.image(image).toString()
        val imageURL = imageStr.substringAfter("src=\"").substringBefore("\"")


        val summary = slideSummary.answer(list, api = chat)
        slideTask = ui.newTask(false).apply { slideTabs["Summary"] = placeholder }
        slideTask.header("Summary")
        slideTask.add(renderMarkdown(summary, ui = ui))
        val mp3data = partition(summary).map {
            narrator.setOpenAI(
                ApplicationServices.clientManager.getOpenAIClient(session, user)
            ).answer(listOf(it), api = chat).mp3data
        }
        val mp3links =
            mp3data.withIndex().map { (i, it) -> if (null != it) slideTask.saveFile("slide$idx-$i.mp3", it) else "" }
        mp3links.forEach { slideTask.add("""<audio preload="none" controls><source src='$it' type='audio/mpeg'></audio>""") }
        val slideContent = slideLayout.answer(list, api = chat)
            .trim()
            .replace("image.png", imageURL)
            .removePrefix("```html\n")
            .removeSuffix("\n```")
        val refBase = "fileIndex/$session/"
        dataStorage.getSessionDir(user, session).resolve("slide_$idx.html").writeText(
            """  
                      |<html>
                      |<body>
                      |${slideContent.replace(refBase, "")}
                      |</body>
                      |</html>
                    """.trimMargin()
        )
        slideTask = ui.newTask(false).apply { slideTabs["Slide"] = placeholder }
        slideTask.header("Content")
        slideTask.add(renderMarkdown("```html\n${slideContent}\n```"))
        slideTask.add("<a href='${refBase}slide_$idx.html'>Slide $idx generated</a>")
        //ui.newTask(false)
        slideTask.apply {
            //reportTabs[i.toString()] = placeholder
            complete("""
                |<iframe src="${refBase}slide_$idx.html" class='slide-content' style="width: 100%; height: 600px; border: none;"></iframe>
                """.trimMargin())
        }


        SlideContents(
            slideContent = slideContent,
            speakingNotes = summary,
            image = imageStr,
            mp3links = mp3links,
        )
    } catch (e: Throwable) {
        slideTask.error(ui, e)
        null
    } finally {
        slideTask.complete("Slide $idx complete.")
    }

    private fun writeReports(
        slideContents: List<SlideContents>,
        task: SessionTask
    ) {
        val reportTabs = TabbedDisplay(task)
        val reportTask = ui.newTask(false).apply { reportTabs["Slides"] = placeholder }
        val refBase = "fileIndex/$session/"
        dataStorage.getSessionDir(user, session).resolve("slides.html").writeText(
            """
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
                slideContents.withIndex().joinToString("\n") { (i, slide) ->
                    ui.newTask(false).apply {
                        reportTabs[i.toString()] = placeholder 
                        complete("""
                            |<iframe src="${refBase}slide_$i.html" class='slide-content' style="width: 100%; height: 600px; border: none;"></iframe>
                            """.trimMargin())
                    }
                    """
          |
          |<div class='slide-container' id='slide$i'>
          |  ${
                        slide.mp3links?.joinToString("\n") { mp3link ->
                            """<audio preload="none" controls><source src='${
                                mp3link.replace(
                                    refBase,
                                    ""
                                )
                            }' type='audio/mpeg'></audio>"""
                        } ?: ""
                    }
          |  <iframe src="slide_$i.html" class='slide-content' style="width: 100%; height: 600px; border: none;"></iframe>
          |</div>
          |
          """.trimMargin()
                }
            }
          |</body>
          |</html>
          """.trimMargin())
        reportTask.add("<a href='${refBase}slides.html'>Slides generated</a>")

        dataStorage.getSessionDir(user, session).resolve("notes.html").writeText("""
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
            slideContents.withIndex().joinToString("\n") { (i, it) ->
                """
          |
          |<div class='slide-container' id='slide$i'>
          |  ${
                    it.mp3links?.joinToString("\n") { mp3link ->
                        """<audio preload="none" controls><source src='${
                            mp3link.replace(
                                refBase,
                                ""
                            )
                        }' type='audio/mpeg'></audio>"""
                    } ?: ""
                }
          |  <div class='slide-notes'>
          |  ${
                    renderMarkdown(
                        """
          |```markdown
          |${it.speakingNotes}
          |```""".trimMargin(), ui = ui
                    )
                }
          |</div>
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |</div>
          |
          """.trimMargin()
            }
        }
          |</body>
          |</html>
          """.trimMargin())
        reportTask.add("<a href='${refBase}notes.html'>Notes generated</a>")

        dataStorage.getSessionDir(user, session).resolve("combined.html").writeText("""
          |<html>
          |<head>
          |<style>
          |$css
          |</style>
          |</head>
          |<body>
          |$playAll
          |
          |${
            slideContents.withIndex().joinToString("\n") { (i, it) ->
                """
          |
          |<div class='slide-container' id='slide$i'>
          |  <div class='slide-image'>${it.image.replace(refBase, "")}</div>
          |  ${
                    it.mp3links?.joinToString("\n") { mp3link ->
                        """<audio preload="none" controls><source src='${
                            mp3link.replace(
                                refBase,
                                ""
                            )
                        }' type='audio/mpeg'></audio>"""
                    } ?: ""
                }
          |  <div class='slide-content'>${it.slideContent.replace(refBase, "")}</div>
          |  <div class='slide-notes'>${renderMarkdown(it.speakingNotes, ui = ui)}</div>
          |</div>
          |
          """.trimMargin()
            }
        }
          |</body>
          |</html>
          """.trimMargin()
        )
        reportTask.complete("<a href='${refBase}combined.html'>Combined report generated</a>")
    }

    private val css = """
    |.slide-container {
    |  overflow: auto;
    |  width: 100%;
    |  height: -webkit-fill-available;
    |}
    |""".trimMargin()

    @Language("HTML")
    private val playAll = """
    |<button id='playAll'>Play All Slides</button>
    |<script>
    |    document.getElementById('playAll').addEventListener('click', function () {
    |        const slides = document.querySelectorAll('.slide-container');
    |        let currentSlide = 0;
    |        let currentAudio = 0;
    |        
    |        function playNextSlide() {
    |            if (currentSlide >= slides.length) return;
    |            const slide = slides[currentSlide];
    |            const audios = slide.querySelectorAll('audio');
    |            if (currentAudio >= audios.length) {
    |                currentSlide++;
    |                currentAudio = 0;
    |                playNextSlide();
    |                return;
    |            }
    |            const audio = audios[currentAudio];
    |            const image = slide.querySelector('.slide-content');
    |            if (audio) {
    |                image.scrollIntoView({behavior: 'smooth', block: 'start', inline: 'start'});
    |                audio.play();
    |                audio.onended = function () {
    |                    currentAudio++;
    |                    playNextSlide();
    |                };
    |            } else {
    |                currentAudio++;
    |                playNextSlide();
    |            }
    |        }
    |
    |        playNextSlide();
    |    });
    |</script>
    |""".trimMargin()

    private fun partition(speakingNotes: String): List<String> = when {
        speakingNotes.length < 4000 -> listOf(speakingNotes)
        else -> StringSplitter.split(
            speakingNotes, mapOf(
                "." to 2.0,
                " " to 1.0,
                ", " to 2.0,
            )
        ).toList().flatMap { partition(it) }
    }

    data class SlideContents(
        val slideContent: String,
        val speakingNotes: String,
        val image: String,
        val mp3links: List<String>? = null,
    )

    companion object
}


class PresentationDesignerActors(
    val model: OpenAITextModel = OpenAIModels.GPT4o,
    val temperature: Double = 0.3,
    voice: String = "alloy",
    voiceSpeed: Double = 1.0,
) {

    private val initialAuthor = ParsedActor(
        resultClass = Outline::class.java,
        exampleInstance = Outline(
            slides = listOf(
                SlideInfo(
                    title = "Introduction",
                    content = "Introduce the topic and provide an overview of the presentation."
                ),
                SlideInfo(
                    title = "Main Points",
                    content = "List the main points that will be covered in the presentation."
                ),
                SlideInfo(
                    title = "Conclusion",
                    content = "Summarize the main points and provide a call to action."
                )
            )
        ),
        model = OpenAIModels.GPT4o,
        parsingModel = OpenAIModels.GPT4oMini,
        prompt = """
            You are a high-level presentation planner. Based on an input topic, provide a list of slides with a brief description of each.
        """.trimIndent()
    )

    data class SlideInfo(
        @Description("The title of the slide.")
        val title: String? = null,
        @Description("The detailed content for each outline point.")
        val content: String? = null
    ) : ValidatedObject {
        override fun validate() = when {
            title.isNullOrBlank() -> "title is required"
            else -> null
        }
    }

    data class Outline(
        val slides: List<SlideInfo>? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            slides.isNullOrEmpty() -> "items are required"
            else -> null
        }
    }

    private val contentExpander = SimpleActor(
        model = OpenAIModels.GPT4o,
        prompt = """
      You are an assistant that expands outlines into detailed content. 
      Given content for a presentation and a topic/slide to expand, provide detailed content for that slide.
      """.trimIndent(),
    )

    private val slideSummarizer = SimpleActor(
        prompt = """
        You are a presentation assistant. Your task is to create a speaking transcript from content. 
        When you receive content, rewrite it in a more concise and engaging form.
        Do not include formatting in the output.
        """.trimIndent(),
        name = "StyleFormatter",
        model = OpenAIModels.GPT4oMini,
        temperature = 0.3
    )

    private val slideFormatter = SimpleActor(
        prompt = """
        You are a presentation slide designer. Your task is to present content it in a visually appealing and consumable form. 
        When you receive content, format it using HTML and CSS to create a professional and polished look.
        The HTML output should be contained within a div with class="slide" with an aspect ratio of about 16:9.
        In addition, decorate the slide with an image named "image.png" with proper sizing and placement.
        Output raw HTML with inline CSS styling.
        """.trimIndent(),
        name = "StyleFormatter",
        model = OpenAIModels.GPT4oMini,
        temperature = 0.3
    )

    private val imageRenderer = ImageActor(
        prompt = """
            Your task is to provide a useful image to accompany the content provided to you.
            You will reply with an image description which will then be rendered.
        """.trimIndent(),
        name = "ImageRenderer",
        imageModel = ImageModels.DallE3,
        textModel = OpenAIModels.GPT4oMini,
        temperature = 0.3
    )

    private val narrator = TextToSpeechActor(voice = voice, speed = voiceSpeed, models = OpenAIModels.GPT4oMini)

    enum class ActorType {
        INITIAL_AUTHOR,
        CONTENT_EXPANDER,
        SLIDE_SUMMARY,
        SLIDE_LAYOUT,
        IMAGE_RENDERER,
        NARRATOR,
    }

    val actorMap: Map<ActorType, BaseActor<out Any, out Any>> = mapOf(
        ActorType.INITIAL_AUTHOR to initialAuthor,
        ActorType.CONTENT_EXPANDER to contentExpander,
        ActorType.SLIDE_LAYOUT to slideFormatter,
        ActorType.SLIDE_SUMMARY to slideSummarizer,
        ActorType.IMAGE_RENDERER to imageRenderer,
        ActorType.NARRATOR to narrator,
    )

    companion object {
        val log = LoggerFactory.getLogger(PresentationDesignerActors::class.java)
    }
}