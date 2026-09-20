package rpc

import "time"

// handleChannelSnapshot returns a validated, complete channel inventory. Query
// failure is never represented as an empty list and the read is never journaled.
func (d *Dispatcher) handleChannelSnapshot(req *JsonRpcRequest) *JsonRpcResponse {
	if d.esl == nil || d.gov == nil {
		return NewErrorResponse(req.ID, -32000, "Channel snapshot unavailable", nil)
	}
	started := time.Now().UnixMilli()
	uuids, err := d.esl.ChannelUUIDs()
	if err != nil {
		return NewErrorResponse(req.ID, -32000, "Channel snapshot unavailable", nil)
	}
	response := NewFNodeSuccessResponse(req.ID, d.gov.NodeID(), "", "", 200, "OK")
	response.Result.(*FNodeResult).Data = map[string]interface{}{"complete": true, "channel_uuids": uuids, "started_at": started, "completed_at": time.Now().UnixMilli()}
	return response
}
