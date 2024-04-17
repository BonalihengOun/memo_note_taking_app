package com.example._memo_noted_takingapp.Controller;
import com.example._memo_noted_takingapp.Model.NotePaper;
import com.example._memo_noted_takingapp.Model.dto.Request.NotePaperRequest;
import com.example._memo_noted_takingapp.Model.dto.Response.APIResponse;
import com.example._memo_noted_takingapp.Repositority.NotePaperRepo;
import com.example._memo_noted_takingapp.Repositority.Tags_noteRepo;
import com.example._memo_noted_takingapp.Service.NotePaperService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/memo/notes/")
@SecurityRequirement(name = "bearerAuth")
public class NoteController {
    private final NotePaperService notePaperService;
    private final Tags_noteRepo tags_noteRepo;
    Date currentDate = new Date();
    private final NotePaperRepo notePaperRepo;
    public NoteController(NotePaperService notePaperService, Tags_noteRepo tagsNoteRepo, NotePaperRepo notePaperRepo) {
        this.notePaperService = notePaperService;
        tags_noteRepo = tagsNoteRepo;
        this.notePaperRepo = notePaperRepo;
    }
    @GetMapping
    public ResponseEntity<APIResponse<List<NotePaper>>> getAllNotes() {
        return ResponseEntity.status(HttpStatus.OK).body(
                new APIResponse<>(
                        "Find all notes successful",
                        notePaperService.getAllNotes(),
                        HttpStatus.OK, new Date()));
    }
    @GetMapping("{id}")
    public ResponseEntity<APIResponse<NotePaper>> getNoteById(@PathVariable @Valid @Positive(message = "must be greater than 0") Integer id) {
        return ResponseEntity.status(HttpStatus.OK).body(new APIResponse<>(
                "Note With ID: "+id+"  is found successful",
                notePaperService.getNotesById(id),
                HttpStatus.OK,new Date()));
    }
    @GetMapping("title/{title}")
    public ResponseEntity<APIResponse<List<NotePaper>>> getNoteByTitle(@PathVariable String title) {
        List<NotePaper> foundNotes = notePaperRepo.searchTitleIgnoreCase(title);

        System.out.println(foundNotes);
        if (foundNotes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new APIResponse<>("No notes found with the title: " + title, null, HttpStatus.NOT_FOUND,new Date())
            );
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new APIResponse<>("Search note successful", foundNotes, HttpStatus.OK, new Date())
            );
        }
    }


    @PostMapping
    public ResponseEntity<APIResponse<NotePaper>> addNote(@RequestBody @Valid NotePaperRequest notePaperRequest) {

        NotePaper notePaper = notePaperService.saveNotes(notePaperRequest);
        notePaperRequest.setCreationDate(currentDate);
        System.out.println(notePaperRequest);
        for (Integer Id : notePaperRequest.getTagsLists()) {
            tags_noteRepo.insertTag(notePaper.getNotedId(),Id);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new APIResponse<>("Note is created successfully",
                     notePaperService.getNotesById(notePaper.getNotedId()),
                        HttpStatus.OK, new Date())
        );
    }

    @PutMapping("{id}")
    public ResponseEntity<APIResponse<NotePaper>> updateNote(@PathVariable @Valid @Positive(message = "must be greater than 0") Integer id, @RequestBody NotePaperRequest notePaperRequest) {
        NotePaper notePaper = notePaperService.updateNote(id, notePaperRequest);
        notePaperRequest.setCreationDate(currentDate);
        tags_noteRepo.removeTag(notePaper.getNotedId());
        for (Integer Id : notePaperRequest.getTagsLists()) {
            tags_noteRepo.insertTag(notePaper.getNotedId(),Id);
        }

        System.out.println(notePaperRequest);
        if (!notePaperRequest.getTagsLists().isEmpty()) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    new APIResponse<>("Note with Id: " + id + " is updated successfully",
                            notePaperService.getNotesById(notePaper.getNotedId()),
                            HttpStatus.OK,
                            new Date())
            );
        }else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new APIResponse<>("Note with Id: " + id + " is not found",
                            notePaper,
                            HttpStatus.OK,new Date())

            );
        }
    }
    @DeleteMapping("{id}")
    public ResponseEntity<String> removeNote(@PathVariable @Valid @Positive(message = "must be greater than 0") Integer id) {
       String message =  notePaperService.deleteNote(id);
        System.out.println(message);
       return ResponseEntity.status(HttpStatus.OK).body(message);
    }
}
