package org.chandler25.ai.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.chandler25.ai.demo.domain.bo.LabelNode;
import org.chandler25.ai.demo.domain.dto.LabelDTO;
import org.chandler25.ai.demo.domain.dto.NoteDTO;
import org.chandler25.ai.demo.respository.entity.Note;
import org.chandler25.ai.demo.service.NoteService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 类功能描述
 *
 * @author 钱丁君-chandler 2025/11/21 14:37
 * @version 1.0.0
 * @since 21
 */
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@Tag(name = "笔记管理", description = "笔记管理")
public class NoteController {

    private final NoteService noteService;

    @Operation(summary = "查询用户的所有笔记", description = "查询用户的所有笔记")
    @GetMapping("/query/all")
    public List<Note> queryNotes() {
        return noteService.queryNotes();
    }

    @Operation(summary = "查询单个笔记", description = "查询单个笔记")
    @GetMapping("/query/{id}")
    public Note queryNoteById(@RequestParam Long id) {
        return noteService.queryNoteById(id);
    }

    @Operation(summary = "新增笔记", description = "新增笔记")
    @PostMapping("/add")
    public void addNote(@Valid @RequestBody NoteDTO note) {
        noteService.addNote(note);
    }

    @Operation(summary = "修改笔记", description = "修改笔记")
    @PostMapping("/update")
    public void updateNote(@Valid @RequestBody NoteDTO note) {
        noteService.updateNote(note);
    }

    @Operation(summary = "删除笔记", description = "删除笔记")
    @DeleteMapping("/del")
    public void delNote(@RequestParam Long noteId) {
        noteService.delNote(noteId);
    }

    @Operation(summary = "新增或修改标签", description = "新增或修改标签")
    @PostMapping("/label/add")
    public void addOrUpdateLabel(@Valid @RequestBody LabelDTO label) {
        noteService.addOrUpdateLabel(label);
    }

    @Operation(summary = "删除标签", description = "删除标签")
    @DeleteMapping("/label/del")
    public void delLabel(Long labelId) {
        noteService.delLabel(labelId);
    }

    @Operation(summary = "笔记关联标签", description = "笔记关联标签")
    @PostMapping("/relate")
    public void relate(@RequestParam Long labelId, @RequestParam Long noteId) {
        noteService.relate(labelId, noteId);
    }

    @Operation(summary = "查询所有标签", description = "查询所有标签")
    @GetMapping("/label/query/all")
    public List<LabelNode> queryAllLabels() {
        return noteService.queryAllLabels();
    }

    @Operation(summary = "查询子标签", description = "查询子标签")
    @GetMapping("/label/query/children")
    public LabelNode querySubLabel(Long labelId){
        return noteService.querySubLabel(labelId);
    }
}