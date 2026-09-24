package event

import (
	"encoding/json"
	"encoding/xml"
	"os"
	"testing"
)

// TestPhoneBindingEntryNormalization verifies that the trusted dialplan marker
// and authenticated extension reach the Java control plane without defaults.
func TestPhoneBindingEntryNormalization(t *testing.T) {
	normalizer := NewNormalizer("fs-node-01")
	result := normalizer.Normalize(map[string]string{
		"Event-Name":                 "CHANNEL_PARK",
		"Unique-ID":                  "binding-channel-01",
		"Caller-Destination-Number":  "0000",
		"variable_fcc_flow_entry":    "PHONE_BINDING",
		"variable_sip_auth_username": "1001",
	})
	if result == nil {
		t.Fatal("phone binding park event must be normalized")
	}

	var fixture struct {
		Params struct {
			Parameters map[string]string `json:"params"`
		} `json:"params"`
	}
	if err := json.Unmarshal(result.RawJSON, &fixture); err != nil {
		t.Fatalf("decode normalized event: %v", err)
	}
	if fixture.Params.Parameters["flow_entry"] != "PHONE_BINDING" {
		t.Fatalf("unexpected flow entry: %q", fixture.Params.Parameters["flow_entry"])
	}
	if fixture.Params.Parameters["authenticated_extension"] != "1001" {
		t.Fatalf("unexpected authenticated extension: %q", fixture.Params.Parameters["authenticated_extension"])
	}

	withoutMarker := normalizer.Normalize(map[string]string{
		"Event-Name":                 "CHANNEL_PARK",
		"Unique-ID":                  "binding-channel-02",
		"Caller-Destination-Number":  "0000",
		"variable_sip_auth_username": "1001",
	})
	if withoutMarker == nil {
		t.Fatal("park event without a marker must still be observable")
	}
	fixture.Params.Parameters = nil
	if err := json.Unmarshal(withoutMarker.RawJSON, &fixture); err != nil {
		t.Fatalf("decode event without marker: %v", err)
	}
	if _, exists := fixture.Params.Parameters["flow_entry"]; exists {
		t.Fatal("normalizer must not invent a trusted flow entry")
	}
}

// TestCommandResultNormalization verifies physical key events stay separate
// from the final result of a ReadDTMF command.
func TestCommandResultNormalization(t *testing.T) {
	normalizer := NewNormalizer("fs-node-01")
	keyPress := normalizer.Normalize(map[string]string{
		"Event-Name":     "DTMF",
		"Unique-ID":      "binding-channel-01",
		"DTMF-Digit":     "9",
		"Core-UUID":      "boot-1",
		"Event-Sequence": "1",
	})
	if keyPress == nil || keyPress.Category != "dtmf" {
		t.Fatal("physical key press must remain Event.DTMF")
	}

	completed := normalizer.Normalize(map[string]string{
		"Event-Name":                  "CHANNEL_EXECUTE_COMPLETE",
		"Unique-ID":                   "binding-channel-01",
		"Application":                 "play_and_get_digits",
		"variable_dtmf_val":           "901001",
		"variable_fcc_command_id":     "binding-dtmf-9001",
		"variable_fcc_command_method": "FNode.ReadDTMF",
		"variable_ctrl_uuid":          "ctrl-9001",
		"Core-UUID":                   "boot-1",
		"Event-Sequence":              "2",
	})
	if completed == nil || completed.Category != "command" {
		t.Fatal("ReadDTMF completion must become Event.CommandResult")
	}
	var fixture struct {
		Method string                   `json:"method"`
		Params CommandResultEventParams `json:"params"`
	}
	if err := json.Unmarshal(completed.RawJSON, &fixture); err != nil {
		t.Fatalf("decode command result event: %v", err)
	}
	if fixture.Method != "Event.CommandResult" ||
		fixture.Params.CommandID != "binding-dtmf-9001" ||
		fixture.Params.Result["dtmf"] != "901001" {
		t.Fatalf("unexpected command result: %+v", fixture.Params)
	}
	if stalePlayback := normalizer.Normalize(map[string]string{
		"Event-Name": "CHANNEL_EXECUTE_COMPLETE", "Unique-ID": "binding-channel-01",
		"Application": "playback", "variable_fcc_command_id": "binding-dtmf-9001",
		"variable_fcc_command_method": "FNode.ReadDTMF",
	}); stalePlayback != nil {
		t.Fatal("a subsequent playback must not complete the previous ReadDTMF command")
	}
	failed := normalizer.Normalize(map[string]string{
		"Event-Name": "CHANNEL_EXECUTE_COMPLETE", "Unique-ID": "binding-channel-01",
		"Application": "playback", "Application-Response": "-ERR FILE NOT FOUND",
		"variable_fcc_command_id":     "binding-result-9001",
		"variable_fcc_command_method": "FNode.Play",
	})
	if failed == nil {
		t.Fatal("failed playback must have a final result")
	}
	if err := json.Unmarshal(failed.RawJSON, &fixture); err != nil {
		t.Fatal(err)
	}
	if fixture.Params.CommandStatus != CommandResultFailed || fixture.Params.Code >= 0 {
		t.Fatalf("playback failure reported success: %+v", fixture.Params)
	}
}

// TestPhoneBindingDialplanContract verifies the deployable sample remains
// parseable and declares the exact marker consumed by the control plane.
func TestPhoneBindingDialplanContract(t *testing.T) {
	content, err := os.ReadFile("../examples/freeswitch/dialplan/default/10_fcc_phone_binding.xml")
	if err != nil {
		t.Fatalf("read phone binding dialplan: %v", err)
	}
	var document struct {
		Extension struct {
			Name       string `xml:"name,attr"`
			Conditions []struct {
				Field      string `xml:"field,attr"`
				Expression string `xml:"expression,attr"`
				Break      string `xml:"break,attr"`
				Actions    []struct {
					Application string `xml:"application,attr"`
					Data        string `xml:"data,attr"`
				} `xml:"action"`
				AntiActions []struct {
					Application string `xml:"application,attr"`
					Data        string `xml:"data,attr"`
				} `xml:"anti-action"`
			} `xml:"condition"`
		} `xml:"extension"`
	}
	if err := xml.Unmarshal(content, &document); err != nil {
		t.Fatalf("parse phone binding dialplan: %v", err)
	}
	if document.Extension.Name != "fcc-phone-binding" || len(document.Extension.Conditions) != 2 {
		t.Fatalf("unexpected phone binding extension: %+v", document.Extension)
	}
	if document.Extension.Conditions[0].Expression != "^0000$" || document.Extension.Conditions[0].Break != "on-false" {
		t.Fatalf("unexpected binding number condition: %+v", document.Extension.Conditions[0])
	}
	authCondition := document.Extension.Conditions[1]
	if authCondition.Field != "${sip_auth_username}" || authCondition.Expression != "^[0-9]{2,20}$" {
		t.Fatalf("unexpected authenticated extension condition: %+v", authCondition)
	}
	actions := authCondition.Actions
	if len(actions) != 2 || actions[0].Application != "set" || actions[0].Data != "fcc_flow_entry=PHONE_BINDING" || actions[1].Application != "park" {
		t.Fatalf("trusted entry marker or binding actions changed: %+v", actions)
	}
	for _, action := range actions {
		if action.Application == "answer" {
			t.Fatal("dialplan must not answer before fcc-server sends FNode.Answer")
		}
	}
	if len(authCondition.AntiActions) != 1 || authCondition.AntiActions[0].Application != "hangup" {
		t.Fatalf("missing unauthenticated-call rejection: %+v", authCondition.AntiActions)
	}
}
