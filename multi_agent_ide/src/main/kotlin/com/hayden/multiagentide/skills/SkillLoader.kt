package com.hayden.multiagentide.skills

import com.embabel.agent.skills.Skills
import org.springframework.stereotype.Component

@Component
class SkillLoader {

    fun loadSkill(name: String, description: String): Skills {
        return Skills(name, description)
    }

}