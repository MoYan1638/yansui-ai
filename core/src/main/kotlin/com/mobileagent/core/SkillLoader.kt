package com.mobileagent.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.nio.file.Files
import java.nio.file.Path

/**
 * M2 Skill 加载器：解析仓库 skills/ 目录下的技能包。
 * 技能包格式：目录 + SKILL.md（YAML 风格 front-matter：name/description，正文为注入内容）。
 * 每个 skill 目录可带 references/ 子目录（正文里说明何时读，模型用 read_file 按需加载，避免一次性撑爆上下文）。
 */
data class Skill(
    val name: String,
    val description: String,
    val body: String,
    val dir: Path
)

object SkillLoader {

    private val frontMatterRegex = Regex("""^---\s*\r?\n(.*?)\r?\n---\s*\r?\n""", RegexOption.DOT_MATCHES_ALL)

    /** 扫描 skillsRoot 下所有含 SKILL.md 的一级子目录 */
    fun loadAll(skillsRoot: Path): List<Skill> {
        if (!Files.isDirectory(skillsRoot)) return emptyList()
        val dirs = Files.list(skillsRoot).use { stream -> stream.collect(java.util.stream.Collectors.toList()) }
        return dirs.filter { Files.isDirectory(it) }
            .mapNotNull { parse(it) }
            .sortedBy { it.name }
    }

    /** 解析单个技能目录；没有合法 SKILL.md 的目录返回 null */
    fun parse(dir: Path): Skill? {
        val md = dir.resolve("SKILL.md")
        if (!Files.exists(md)) return null
        val text = md.readTextCompat()
        val fmMatch = frontMatterRegex.find(text) ?: return null
        val fm = fmMatch.groupValues[1]
        fun field(key: String): String? =
            Regex("""^\s*$key\s*:\s*(.+)$""", RegexOption.MULTILINE).find(fm)
                ?.groupValues?.get(1)?.trim()
        val name = field("name") ?: dir.fileName.toString()
        val description = field("description") ?: ""
        val body = text.substring(fmMatch.value.length).trim()
        if (body.isBlank()) return null
        return Skill(name, description, body, dir)
    }

    /**
     * 合规闸门（love-companion 硬性要求，通用化为"带闸门的技能"）：
     * 技能目录下若存在 gate.json（{"setting":"data/love-companion/settings.json",
     * "field":"合规.已满18周岁确认"}），对应字段未置 true 时正文不注入，只注入闸门提示。
     * 这是《人工智能拟人化互动服务管理暂行办法》第二章第十四条的要求，不提供绕过。
     */
    fun applyGate(skill: Skill, workspaceRoot: Path): Skill {
        val gateFile = skill.dir.resolve("gate.json")
        if (!Files.exists(gateFile)) return skill
        return try {
            val gate = Json.parseToJsonElement(gateFile.readTextCompat()).jsonObject
            val settingRel = gate["setting"]?.jsonPrimitive?.content ?: return skill
            val field = gate["field"]?.jsonPrimitive?.content ?: return skill
            val notice = gate["notice"]?.jsonPrimitive?.content ?: "该技能需要先完成合规确认才能启用。"
            val settingsPath = workspaceRoot.resolve(settingRel).normalize()
            val confirmed = Files.exists(settingsPath) && runCatching {
                val json = Json.parseToJsonElement(settingsPath.readTextCompat()).jsonObject
                field.split('.').fold(json as kotlinx.serialization.json.JsonElement) { acc, key ->
                    (acc as? kotlinx.serialization.json.JsonObject)?.get(key)
                        ?: return@runCatching null
                }?.jsonPrimitive?.booleanOrNull
            }.getOrNull() == true
            if (confirmed) skill
            else skill.copy(body = "【合规闸门】${skill.name} 未启用：$notice\n收到与该技能相关的请求时，向用户说明需要完成合规确认（$field），不要注入人设与记忆。")
        } catch (e: Exception) {
            skill.copy(body = "【合规闸门】${skill.name} 的 gate.json 解析失败，技能未启用。")
        }
    }
}
