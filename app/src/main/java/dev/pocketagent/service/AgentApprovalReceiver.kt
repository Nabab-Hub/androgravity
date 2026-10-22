package dev.pocketagent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pocketagent.PocketAgentApp

class AgentApprovalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: return
        val agentLoop = PocketAgentApp.instance.agentLoop

        when (intent.action) {
            ACTION_APPROVE -> {
                agentLoop.approve(approvalId)
            }
            ACTION_DENY -> {
                agentLoop.deny(approvalId)
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "dev.pocketagent.ACTION_APPROVE"
        const val ACTION_DENY = "dev.pocketagent.ACTION_DENY"
        const val EXTRA_APPROVAL_ID = "extra_approval_id"
    }
}
