package com.simiacryptus.skyenet

import com.simiacryptus.openai.OpenAIClient
import com.simiacryptus.openai.proxy.ChatProxy
import com.simiacryptus.skyenet.body.ChatSessionFlexmark
import com.simiacryptus.skyenet.body.PersistentSessionBase
import com.simiacryptus.skyenet.body.SessionDiv
import com.simiacryptus.skyenet.body.SkyenetMacroChat
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.describe.Description

class CookbookGenerator(
    applicationName: String,
    baseURL: String,
    oauthConfig: String? = null,
    temperature: Double = 0.3
) : SkyenetMacroChat(
    applicationName = applicationName,
    baseURL = baseURL,
    temperature = temperature,
    oauthConfig = oauthConfig
) {
    interface CookbookAuthorAPI {
        fun parseRecipeSpec(spec: String): RecipeSpec

        data class RecipeSpec(
            val title: String,
            val cuisine: String,
            val targetAudience: TargetAudience,
            val ingredients: List<String>,
            val equipment: List<String>,
        )

        data class TargetAudience(
            val ageGroup: String
        )

        data class WritingStyle(
            val targetAudience: TargetAudience,
            val notes: MutableMap<String,String> = mutableMapOf()
        )

        fun createRecipes(spec: RecipeSpec, count: Int): RecipeList

        data class RecipeList(
            val recipeList: List<RecipeSummary>
        )

        data class RecipeSummary(
            @Description("A very brief description of the recipe for listing/indexing purposes")
            val title: String,
            @Description("A brief description of the recipe")
            val description: String,
            @Description("A list of ingredients used; this should be limited to 2-5 items, usually drawn from the available recipe ingredients")
            val ingredients: List<String>,
            @Description("A list of equipment used; this should be limited to what is strictly needed, and drawn from the available recipe equipment")
            val equipment: List<String>,
        )

        data class RecipeDetails(
            @Description("A very brief description of the recipe for listing/indexing purposes")
            val title: String,
            @Description("A narrative description of the relevant cuisine background")
            val background: String,
            @Description("A narrative description of the recipe")
            val description: String,
            @Description("A map of ingredients used to the quantities required for the recipe")
            val ingredients: Map<String, String>,
            @Description("A map of equipment to quantity required for the recipe")
            val equipment: Map<String, Int>,
            @Description("A checklist for recipe preparation")
            val kitchenSetup: List<String>,
            @Description("A list of steps to perform the recipe")
            val steps: List<RecipeStepData>,
            @Description("A list of ideas for variations on the recipe")
            val variations: List<RecipeVariation>,
            val supervisionNotes: List<String>,
        )

        data class RecipeStepData(
            val step: String,
            val observations: List<String>,
        )

        data class RecipeVariation(
            val title: String,
            val details: String,
            @Description("A map of observations to make during the cooking (with expected results)")
            val observations: Map<String, String>,
        )

        fun detailRecipe(recipe: RecipeSummary): RecipeDetails


        @Description("Create a detailed cookbook writeup")
        fun getFullCookbook(
            style: WritingStyle,
            recipe: RecipeDetails
        ): Cookbook

        fun modifyCookbook(
            cookbook: String,
            usertext: String
        ): Cookbook

        data class Cookbook(
            val markdown: String
        )
    }

    val cookbookAuthorAPI = ChatProxy(
        clazz = CookbookAuthorAPI::class.java,
        api = api,
        model = OpenAIClient.Models.GPT35Turbo,
        temperature = temperature
    ).create()

    override fun processMessage(
        sessionId: String,
        userMessage: String,
        session: PersistentSessionBase,
        sessionUI: SessionUI,
        sessionDiv: SessionDiv
    ) {
        sessionDiv.append("""<div>$userMessage</div>""", true)
        val spec = cookbookAuthorAPI.parseRecipeSpec(userMessage)
        sessionDiv.append("""<div><pre>${JsonUtil.toJson(spec)}</pre></div>""", true)
        val recipes = cookbookAuthorAPI.createRecipes(spec, 20)
        //sessionDiv.apply("""<div><pre>${JsonUtil.toJson(recipes)}</pre></div>""", true)
        for (recipe in recipes.recipeList.toMutableList().shuffled()) {
            sessionDiv.append("""<div><pre>${JsonUtil.toJson(recipe)}</pre>${sessionUI.hrefLink {
                sessionDiv.append("", true)
                val details = cookbookAuthorAPI.detailRecipe(recipe)
                sessionDiv.append("""<div><pre>${JsonUtil.toJson(details)}</pre></div>""", true)
                val fullCookbook = cookbookAuthorAPI.getFullCookbook(
                    style = CookbookAuthorAPI.WritingStyle(
                        targetAudience = spec.targetAudience,
                        notes = mutableMapOf(
                            "description" to "Should be conversational and friendly, introducing the cook to both the recipe and background cuisine",
                            "instructions" to "Fully detailed instructions, including measurements, amounts, notes, and precautions. Use a conversational style that invites the cook to make predictions and includes explanations.",
                            "notes" to "Briefly formatted at the bottom to be readable by the adult with full detail but not attention-drawing"
                        )
                    ),
                    recipe = details,
                )
                postRecipe(sessionDiv, fullCookbook, sessionUI, cookbookAuthorAPI)
            } }Expand</a></div>""", true)
        }
    }

    private fun postRecipe(
        sessionDiv: SessionDiv,
        fullCookbook: CookbookAuthorAPI.Cookbook,
        sessionUI: SessionUI,
        cookbookAuthorAPI: CookbookAuthorAPI
    ) {
        sessionDiv.append(
            """<div>${ChatSessionFlexmark.renderMarkdown(fullCookbook.markdown)}</div>${
                sessionUI.textInput { userInput ->
                    sessionDiv.append("", true)
                    val cookbook = cookbookAuthorAPI.modifyCookbook(fullCookbook.markdown, userInput)
                    postRecipe(sessionDiv, cookbook, sessionUI, cookbookAuthorAPI)
                }
            }""", false)
    }



}