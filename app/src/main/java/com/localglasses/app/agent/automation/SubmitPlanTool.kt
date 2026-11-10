package com.localglasses.app.agent.automation

import com.google.ai.edge.litertlm.OpenApiTool

class SubmitPlanTool : OpenApiTool {

    override fun getToolDescriptionJsonString(): String = SCHEMA

    override fun execute(paramsJsonString: String): String = "{}"

    companion object {
        private val SCHEMA = """
            {
              "name": "submit_plan",
              "description": "Submit the ordered UI steps for one task.",
              "parameters": {
                "type": "object",
                "properties": {
                  "steps": {
                    "type": "array",
                    "items": {
                      "type": "object",
                      "properties": {
                        "type": {
                          "type": "string",
                          "enum": ["open_app", "tap", "type", "scroll", "wait"]
                        },
                        "package": {"type": "string"},
                        "viewId": {"type": "string"},
                        "text": {"type": "string"},
                        "contentDescription": {"type": "string"},
                        "textContains": {"type": "string"},
                        "direction": {"type": "string", "enum": ["forward", "backward"]},
                        "millis": {"type": "integer"}
                      },
                      "required": ["type"]
                    }
                  }
                },
                "required": ["steps"]
              }
            }
        """.trimIndent()
    }
}
