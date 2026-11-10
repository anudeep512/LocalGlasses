package com.localglasses.app.agent.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun planStepDescription(step: PlanStep): String = when (step) {
    is PlanStep.RunAction -> "Run ${step.actionId}"
    is PlanStep.OpenApp -> "Open ${step.packageName}"
    is PlanStep.OpenUri -> "Open ${step.uri}"
    is PlanStep.Tap -> "Tap ${step.target.text ?: step.target.contentDescription ?: "target"}"
    is PlanStep.TypeText -> "Type \"${step.text}\""
    is PlanStep.Scroll -> "Scroll ${step.direction}"
    is PlanStep.Wait -> "Wait ${step.millis} ms"
}

@Composable
fun PlanApprovalSheet(
    plan: Plan,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    onUpdate: ((Plan) -> Plan) -> Unit,
    onReplan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val correction = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("")
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Plan for: ${plan.command}", style = MaterialTheme.typography.titleSmall)

            plan.steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${index + 1}. ${planStepDescription(step)}")
                        if (step is PlanStep.TypeText) {
                            OutlinedTextField(
                                value = step.text,
                                onValueChange = { text ->
                                    onUpdate { current ->
                                        current.replaceStep(index, step.copy(text = text))
                                    }
                                },
                                label = { Text("Text") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (step is PlanStep.RunAction) {
                            step.arguments.forEach { (key, value) ->
                                if (value is String) {
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { text ->
                                            onUpdate { current ->
                                                current.replaceStep(
                                                    index,
                                                    step.copy(arguments = step.arguments + (key to text)),
                                                )
                                            }
                                        },
                                        label = { Text(key) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                    Column {
                        TextButton(
                            onClick = {
                                onUpdate { current ->
                                    current.moveStep(index, index - 1)
                                }
                            },
                            enabled = index > 0,
                        ) { Text("Up") }
                        TextButton(
                            onClick = {
                                onUpdate { current ->
                                    current.moveStep(index, index + 1)
                                }
                            },
                            enabled = index < plan.steps.lastIndex,
                        ) { Text("Down") }
                        TextButton(
                            onClick = {
                                onUpdate { current ->
                                    current.copy(
                                        steps = current.steps.filterIndexed { i, _ -> i != index },
                                        approved = false,
                                    )
                                }
                            },
                        ) { Text("Delete") }
                    }
                }
            }

            OutlinedTextField(
                value = correction.value,
                onValueChange = { correction.value = it },
                label = { Text("Correction (natural language)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove) { Text("Approve") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                TextButton(
                    onClick = {
                        onReplan(correction.value)
                        correction.value = ""
                    },
                    enabled = correction.value.isNotBlank(),
                ) { Text("Re-plan") }
            }
        }
    }
}

private fun Plan.replaceStep(index: Int, step: PlanStep): Plan =
    copy(steps = steps.toMutableList().also { it[index] = step }, approved = false)

private fun Plan.moveStep(from: Int, to: Int): Plan {
    if (to !in steps.indices) return this
    val mutable = steps.toMutableList()
    val moved = mutable.removeAt(from)
    mutable.add(to, moved)
    return copy(steps = mutable, approved = false)
}
