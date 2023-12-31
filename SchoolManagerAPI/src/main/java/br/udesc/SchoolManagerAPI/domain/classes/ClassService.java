package br.udesc.SchoolManagerAPI.domain.classes;

import br.udesc.SchoolManagerAPI.domain.classes.dto.ClassDTO;
import br.udesc.SchoolManagerAPI.domain.student.StudentRepository;
import br.udesc.SchoolManagerAPI.domain.subject.Subject;
import br.udesc.SchoolManagerAPI.domain.subject.SubjectRepository;
import br.udesc.SchoolManagerAPI.domain.subjectRelation.SubjectRelation;
import br.udesc.SchoolManagerAPI.domain.subjectRelation.SubjectRelationRepository;
import br.udesc.SchoolManagerAPI.domain.subjectRelation.dto.CreateSubjectRelationDTO;
import br.udesc.SchoolManagerAPI.domain.subjectRelation.dto.SubjectTeacherRelationDTO;
import br.udesc.SchoolManagerAPI.domain.teacher.Teacher;
import br.udesc.SchoolManagerAPI.domain.teacher.TeacherRepository;
import br.udesc.SchoolManagerAPI.utils.SchoolManagerUtils;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassService {

    @Autowired
    private ClassRepository classRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private SubjectRelationRepository subjectRelationRepository;

    public void create(ClassDTO classDTO) {
        Class aClass = new Class();
        aClass.setName(classDTO.getName());
        aClass.setAcademicCategory(classDTO.getAcademicCategory());
        aClass.setSubjects(subjectRepository.findAllById(classDTO.getSubjectIds()));

        Long teacherManagerId = classDTO.getTeacherManagerId();

        if (teacherManagerId != null) {
            Teacher teacherManager = teacherRepository.findById(teacherManagerId).get();
            aClass.setTeacherManager(teacherManager);
            classRepository.create(aClass.getName(), aClass.getAcademicCategory(), aClass.getTeacherManager().getId(), aClass.getSubjects().stream().map(Subject::getId).collect(Collectors.toList()));

            teacherManager.setManagedClass(aClass);
            teacherRepository.setManagedClass(teacherManager.getId(), teacherManager.getManagedClass().getId());
        } else {
            classRepository.create(aClass.getName(), aClass.getAcademicCategory(), null, aClass.getSubjects().stream().map(Subject::getId).collect(Collectors.toList()));
        }
    }

    @Transactional(readOnly = true)
    public List<ClassDTO> findAll() {
        return this.classRepository.findAll()
                .stream()
                .map(ClassDTO::new)
                .toList();
    }

    public ClassDTO findById(Long id) {
        ClassDTO classDTO = new ClassDTO(this.classRepository.findById(id).get());
        return classDTO;
    }

    @Transactional(readOnly = true)
    public String report() {
        String name = "Número de Alunos por Categoria Acadêmica";
        int colums = 2;
        List<Object[]> rows = this.classRepository.getClassStudentCountByCategory();

        String report = SchoolManagerUtils.buildReport(name, rows, colums);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("report", report);

        return jsonObject.toString();
    }

    public void delete(Long classId) {
        if (studentRepository.existsByaClassId(classId)) {
            throw new RuntimeException("Não é possível remover uma turma com alunos vinculados!");
        }
        Class aClass = this.classRepository.findById(classId).get();

        subjectRelationRepository.deleteAllByaClass(aClass);

        Teacher teacherManager = aClass.getTeacherManager();

        if (teacherManager != null) {
            teacherManager.setManagedClass(null);
            aClass.setTeacherManager(null);
            this.teacherRepository.save(teacherManager);
            this.classRepository.save(aClass);
        }

        this.classRepository.deleteById(classId);
    }

    public void edit(Long id, ClassDTO classDTO) {
        Class aClass = this.classRepository.findById(id).get();

        aClass.setName(classDTO.getName());
        aClass.setSubjects(this.subjectRepository.findAllById(classDTO.getSubjectIds()));
        aClass.setAcademicCategory(classDTO.getAcademicCategory());

        Long newTeacherId = classDTO.getTeacherManagerId();

        if (newTeacherId != null) {
            Teacher oldTeacher = aClass.getTeacherManager();
            if (oldTeacher != null) {
                oldTeacher.setManagedClass(null);
            }

            Teacher newTeacher = this.teacherRepository.findById(newTeacherId).get();
            aClass.setTeacherManager(newTeacher);
            newTeacher.setManagedClass(aClass);
        }

        this.classRepository.save(aClass);
    }

    public void doSubjectRelation(CreateSubjectRelationDTO createSubjectRelationDTO) {
        Long classId = createSubjectRelationDTO.getClassId();
        Class aClass = this.classRepository.findById(classId).get();

        if (createSubjectRelationDTO.getSubjectTeacherRelationList().isEmpty()) {
            this.subjectRelationRepository.deleteAllByaClass(aClass);
        }

        for (SubjectTeacherRelationDTO dto : createSubjectRelationDTO.getSubjectTeacherRelationList()) {
            Subject subject = this.subjectRepository.findById(dto.getSubjectId()).get();
            Teacher teacher = this.teacherRepository.findById(dto.getTeacherId()).get();

            boolean relationExists = this.subjectRelationRepository.existsByaClassAndTeacherAndSubject(aClass, teacher, subject);

            if (relationExists) {
                continue;
            }

            SubjectRelation subjectRelation = new SubjectRelation();
            subjectRelation.setAClass(aClass);
            subjectRelation.setSubject(subject);
            subjectRelation.setTeacher(teacher);

            this.subjectRelationRepository.save(subjectRelation);
        }
    }

    @Transactional(readOnly = true)
    public String relationsReport() {
        String report = "Lista de Turmas e suas Matérias";
        report += "\n";

        List<Class> classes = classRepository.findAll();

        for (Class aClass : classes) {
            report += "----------------------------";
            report += "\n";
            report += aClass.getName() + ":";
            report += "\n";

            List<SubjectRelation> subjectRelations = subjectRelationRepository.findAllByaClass(aClass);

            for (SubjectRelation subjectRelation : subjectRelations) {
                report += "   " + subjectRelation.getSubject().getName() + " - " + subjectRelation.getTeacher().getName();
                report += "\n";
            }

            report += "----------------------------";
            report += "\n";
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("report", report);

        return jsonObject.toString();
    }
}
