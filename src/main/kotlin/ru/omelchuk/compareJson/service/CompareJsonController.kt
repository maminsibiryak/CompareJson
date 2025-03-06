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
        // Получаем файлы для сравнения
        val leftFile = resourceLoader.getResource("classpath:left.json").file
        val rightFile = resourceLoader.getResource("classpath:right.json").file

        // Создаем классы PoJo из json файлов
        val objectMapper = jacksonObjectMapper()
        val leftJson = objectMapper.readTree(leftFile)
        val rightJson = objectMapper.readTree(rightFile)
        // Результирующий список
        var difference = listOf<String>()

        // Условие для выбора функции сравнения
        // true - сравниваем массивы
        // false - сравниваем объект
        difference = if (leftJson is ArrayNode && rightJson is ArrayNode) {
            compareJsonArrays(leftJson, rightJson, "")
        } else
            compareJson(leftJson, rightJson, "")

        // Кладем в модель список различий
        model.addAttribute("items", difference)
        return "compare"
    }

    // Функция сравнения массивов
    fun compareJsonArrays(
        leftArray: ArrayNode, rightArray: ArrayNode, path: String
    ): List<String> {
        val differences = mutableListOf<String>()

        // Собираем элементы массива в Set и сортируем
        val leftSortedArray = leftArray.elements().asSequence().toList().sortedBy { it.toString() }
        val rightSortedArray = rightArray.elements().asSequence().toList().sortedBy { it.toString() }

        if (leftSortedArray.size != rightSortedArray.size) {
            differences.add("Размер массива '$path' отличается: ${leftSortedArray.size} vs ${rightSortedArray.size}")
        }
        // Проходим по индексам массива для сравнения и взятия значения
        for (i in leftSortedArray.indices) {
            if (i >= rightSortedArray.size) break

            val leftValue = leftSortedArray[i]
            val rightValue = rightSortedArray[i]

            val itemPath = "$path[$i]"
            // Сравниваем значения и если оба объекты, переходим на сравнение полей и рекурсивно вызываем функцию сравнения
            if (leftValue.isObject && rightValue.isObject) {
                differences.addAll(compareJson(leftValue, rightValue, itemPath))
            } else if (leftValue != rightValue) {
                differences.add("Значение '$itemPath' отличается: $leftValue vs $rightValue")
            }
        }

        return differences
    }

    private fun compareJson(leftJson: JsonNode, rightJson: JsonNode, fieldName: String): List<String> {
        val result = mutableListOf<String>()
        // Если два JSON-объекта идентичны — возвращаем пустой список различий.
        if (leftJson == rightJson) return result
        // Собираем все возможные ключи (включая те, которые есть только в одном из файлов).
        val fieldNames = (leftJson.fieldNames().asSequence() + rightJson.fieldNames().asSequence()).toSet()

        for (field in fieldNames) {
            val newPath = if (fieldName.isEmpty()) field else "$fieldName.$field"
            val leftValue = leftJson.get(field)
            val rightValue = rightJson.get(field)

            //  Если поля нет в одном из JSON → фиксируем различие
            //  Если поле — объект → рекурсивно сравниваем вложенные данные
            // Если поле — массив → сравниваем с сортировкой
            // Если простое значение отличается → записываем разницу
            when {
                leftValue == null -> result.add("Поле '$newPath' отсутствует в первом JSON")
                rightValue == null -> result.add("Поле '$newPath' отсутствует во втором JSON")
                leftValue.isObject && rightValue.isObject -> result.addAll(compareJson(leftValue, rightValue, newPath))
                leftValue.isArray && rightValue.isArray -> {
                    // Сортируем массив перед сравнением, чтобы порядок не влиял
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