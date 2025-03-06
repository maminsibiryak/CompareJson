package ru.omelchuk.compareJson.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/compare")
class CompareJsonController(private val resourceLoader: ResourceLoader) {
    @GetMapping
    fun greeting() = "Hello"

    @GetMapping("/json")
    fun compare(model: Model): String {

        val leftFile = resourceLoader.getResource("classpath:left.json").file
        val rightFile = resourceLoader.getResource("classpath:right.json").file

        val objectMapper = jacksonObjectMapper()
        val leftJson = objectMapper.readTree(leftFile)
        val rightJson = objectMapper.readTree(rightFile)
        var difference = listOf<String>()

        difference = if (leftJson is ArrayNode && rightJson is ArrayNode) {
            compareJsonArrays(leftJson, rightJson, "")
        } else
            compareJson(leftJson, rightJson, "")

        model.addAttribute("items", difference)
        return "compare"
    }

    // Функция сравнения массивов
    fun compareJsonArrays(
        leftArray: ArrayNode, rightArray: ArrayNode, path: String
    ): List<String> {
        val differences = mutableListOf<String>()

        val leftSortedArray = leftArray.elements().asSequence().toList().sortedBy { it.toString() }
        val rightSortedArray = rightArray.elements().asSequence().toList().sortedBy { it.toString() }

        if (leftSortedArray.size != rightSortedArray.size) {
            differences.add("Размер массива '$path' отличается: ${leftSortedArray.size} vs ${rightSortedArray.size}")
        }

        for (i in leftSortedArray.indices) {
            if (i >= rightSortedArray.size) break

            val value1 = leftSortedArray[i]
            val value2 = rightSortedArray[i]

            val itemPath = "$path[$i]"

            if (value1.isObject && value2.isObject) {
                differences.addAll(compareJson(value1, value2, itemPath))
            } else if (value1 != value2) {
                differences.add("Значение '$itemPath' отличается: $value1 vs $value2")
            }
        }

        return differences
    }

    private fun compareJson(leftJson: JsonNode, rightJson: JsonNode, fieldName: String): List<String> {
        val result = mutableListOf<String>()
        if (leftJson == rightJson) return result
        val fieldNames = (leftJson.fieldNames().asSequence() + rightJson.fieldNames().asSequence()).toSet()

        for (field in fieldNames) {
            val newPath = if (fieldName.isEmpty()) field else "$fieldName.$field"
            val leftValue = leftJson.get(field)
            val rightValue = rightJson.get(field)

            when {
                leftValue == null -> result.add("Поле '$newPath' отсутствует в первом JSON")
                rightValue == null -> result.add("Поле '$newPath' отсутствует во втором JSON")
                leftValue.isObject && rightValue.isObject -> result.addAll(compareJson(leftValue, rightValue, newPath))
                leftValue.isArray && rightValue.isArray -> {
                    val leftSortedArray = sortJsonArray(leftValue)
                    val rightSortedArray = sortJsonArray(rightValue)
                    if (leftSortedArray != rightSortedArray) {
                        result.add("Массив '$newPath' отличается (${leftSortedArray} vs ${rightSortedArray})")
                    }
                }

                leftValue != rightValue -> result.add("Значение '$newPath' отличается (${leftValue} vs ${rightValue})")
            }
        }

        return result
    }

    // Функция сортировки JSON-массивов перед сравнением
    fun sortJsonArray(array: JsonNode): List<JsonNode> {
        return array.elements().asSequence().toList().sortedBy { it.toString() }
    }
}