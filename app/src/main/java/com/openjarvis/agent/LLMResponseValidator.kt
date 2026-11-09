package com.openjarvis.agent

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object LLMResponseValidator {
    
    private val knownActions = setOf(
        Action.OPEN_APP,
        Action.TAP,
        Action.TYPE,
        Action.PRESS_BACK,
        Action.PRESS_HOME,
        Action.PRESS_RECENTS,
        Action.AI_PROMPT,
        Action.EXTRACT_TEXT,
        Action.ERROR
    )

    private const val MAX_ACTIONS = 8
    
    fun validate(rawResponse: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        val clean = rawResponse
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        
        if (!clean.startsWith("[") && !clean.startsWith("{")) {
            errors.add("Response is not JSON")
            return ValidationResult(false, emptyList(), errors, false)
        }
        
        val parsed = try {
            if (clean.startsWith("{")) {
                JSONArray("[$clean]")
            } else {
                JSONArray(clean)
            }
        } catch (e: JSONException) {
            errors.add("Invalid JSON: ${e.message}")
            return ValidationResult(false, emptyList(), errors, false)
        }
        
        if (parsed.length() == 0 || parsed.length() > MAX_ACTIONS) {
            errors.add("Action plan must contain between 1 and $MAX_ACTIONS actions")
            return ValidationResult(false, emptyList(), errors, false)
        }

        val actions = mutableListOf<Action>()

        for (i in 0 until parsed.length()) {
            val obj = try {
                parsed.getJSONObject(i)
            } catch (e: Exception) {
                errors.add("Item $i is not an object")
                continue
            }
            
            val actionType = obj.optString("action", "")
            if (actionType.isBlank()) {
                errors.add("Item $i missing 'action' field")
                continue
            }
            
            if (actionType !in knownActions) {
                errors.add("Unsupported action type: $actionType")
                continue
            }

            val action = parseAction(obj)
            val missingField = when (action.action) {
                Action.OPEN_APP -> action.packageName.isNullOrBlank() && action.label.isNullOrBlank()
                Action.TAP -> action.text.isNullOrBlank()
                Action.TYPE -> action.value.isNullOrBlank()
                Action.AI_PROMPT -> action.packageName.isNullOrBlank() || action.prompt.isNullOrBlank()
                else -> false
            }
            if (missingField) {
                errors.add("Action $i is missing a required field")
                continue
            }

            actions.add(action)
        }

        return ValidationResult(
            isValid = errors.isEmpty() && actions.isNotEmpty(),
            actions = actions,
            errors = errors,
            wasRepaired = false
        )
    }
    
    private fun parseAction(obj: JSONObject): Action {
        return Action(
            action = obj.getString("action"),
            packageName = obj.optString("package", null).takeIf { it.isNotBlank() },
            label = obj.optString("label", null).takeIf { it.isNotBlank() },
            text = obj.optString("text", null).takeIf { it.isNotBlank() },
            value = obj.optString("value", null).takeIf { it.isNotBlank() },
            hint = obj.optString("hint", null).takeIf { it.isNotBlank() },
            direction = obj.optString("direction", null).takeIf { it.isNotBlank() },
            x = if (obj.has("x")) obj.getInt("x") else null,
            y = if (obj.has("y")) obj.getInt("y") else null,
            distance = obj.optString("distance", null).takeIf { it.isNotBlank() },
            timeoutMs = obj.optLong("timeout_ms", 3000L),
            message = obj.optString("message", null).takeIf { it.isNotBlank() },
            prompt = obj.optString("prompt", null).takeIf { it.isNotBlank() },
            outputKey = obj.optString("outputKey", null).takeIf { it.isNotBlank() }
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val actions: List<Action>,
        val errors: List<String>,
        val wasRepaired: Boolean
    )
}