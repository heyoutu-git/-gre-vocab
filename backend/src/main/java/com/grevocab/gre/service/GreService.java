package com.grevocab.gre.service;

import com.grevocab.auth.mapper.RoleMapper;
import com.grevocab.common.BizException;
import com.grevocab.gre.dto.BookPlan;
import com.grevocab.gre.dto.ReadingSegment;
import com.grevocab.gre.entity.Book;
import com.grevocab.gre.entity.Lesson;
import com.grevocab.gre.entity.Passage;
import com.grevocab.gre.entity.Vocabulary;
import com.grevocab.gre.mapper.BookMapper;
import com.grevocab.gre.mapper.LessonMapper;
import com.grevocab.gre.mapper.PassageMapper;
import com.grevocab.gre.mapper.VocabularyMapper;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GreService {

    private final LessonMapper lessonMapper;
    private final VocabularyMapper vocabMapper;
    private final BookMapper bookMapper;
    private final PassageMapper passageMapper;
    private final PdfParseService pdfParseService;
    private final RoleMapper roleMapper;

    public GreService(LessonMapper lessonMapper, VocabularyMapper vocabMapper, BookMapper bookMapper,
                      PassageMapper passageMapper, PdfParseService pdfParseService, RoleMapper roleMapper) {
        this.lessonMapper = lessonMapper;
        this.vocabMapper = vocabMapper;
        this.bookMapper = bookMapper;
        this.passageMapper = passageMapper;
        this.pdfParseService = pdfParseService;
        this.roleMapper = roleMapper;
    }

    // 管理员可见全部书本；普通用户仅公共书 + 自己的私有书
    private boolean isAdminUid(Long uid) {
        if (uid == null) return false;
        try {
            return roleMapper.findCodesByUserId(uid).contains("admin");
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 学习端（需登录，按用户可见性过滤） =====
    public List<Lesson> listVisibleLessons(Long uid) {
        List<Long> bookIds = visibleBookIds(uid);
        if (bookIds.isEmpty()) return List.of();
        return lessonMapper.findByVisibleBooks(bookIds);
    }

    public List<Lesson> listLessonsOfBook(Long bookId, Long uid) {
        assertVisible(bookId, uid);
        return lessonMapper.findByBook(bookId);
    }

    // ===== 后台管理（admin，可见全部） =====
    public List<Lesson> listLessons() {
        return lessonMapper.findAll();
    }

    public List<Lesson> listLessons(Long bookId) {
        return lessonMapper.findByBook(bookId);
    }

    public Lesson getLesson(Long id, Long uid) {
        Lesson l = lessonMapper.findById(id);
        if (l == null) throw new BizException(404, "课时不存在");
        if (l.getBookId() != null) assertVisible(l.getBookId(), uid);
        return l;
    }

    public List<Vocabulary> listVocabularies(Long lessonId, Long uid) {
        getLesson(lessonId, uid); // 顺便校验可见性
        return vocabMapper.findByLesson(lessonId);
    }

    // ===== 书本列表（目录级可见性） =====
    // 匿名(uid=null)：仅公共书 + 展示书；普通用户：公共书 + 自己的私有书；管理员：全部
    public List<Book> listBooks(Long uid) {
        List<Book> books;
        if (uid == null) {
            books = bookMapper.findCatalogVisible();
        } else if (isAdminUid(uid)) {
            books = bookMapper.findAll();
        } else {
            books = bookMapper.findAllVisible(uid);
        }
        for (Book b : books) b.setMine(uid != null && b.getUserId() != null && b.getUserId().equals(uid));
        return books;
    }

    public Book getBook(Long id, Long uid) {
        Book b = bookMapper.findById(id);
        if (b == null || !isCatalogVisible(b, uid)) throw new BizException(404, "书本不存在");
        b.setMine(uid != null && b.getUserId() != null && b.getUserId().equals(uid));
        return b;
    }

    // 管理员设定「展示书」（未登录可见）
    public void setBookBrowsePublic(Long id, Integer flag) {
        Book b = bookMapper.findById(id);
        if (b == null) throw new BizException(404, "书本不存在");
        bookMapper.updateBrowsePublic(id, (flag != null && flag != 0) ? 1 : 0);
    }

    // ===== 书本管理 =====
    public List<Book> listBooks() {
        return bookMapper.findAll();
    }

    public Long saveBook(Book b) {
        // 公共书：强制清除所有者，避免与 user_id 判定冲突（findCatalogVisible 等以 is_public 为主）
        if (b.getIsPublic() != null && b.getIsPublic() == 1) {
            b.setUserId(null);
        } else if (b.getIsPublic() != null && b.getIsPublic() == 0 && b.getUserId() == null) {
            // 私有书必须归属某用户，默认归管理员，避免产生无主私有书（谁都看不到）
            b.setUserId(1L);
        }
        if (b.getId() == null) {
            bookMapper.insert(b);
        } else {
            bookMapper.update(b);
        }
        return b.getId();
    }

    @Transactional
    public void removeBook(Long id) {
        // 书本删除后，其课时变为未归属（ON DELETE SET NULL），但词汇保留
        bookMapper.delete(id);
    }

    // ===== 后台管理（admin） =====
    public Long saveLesson(Lesson l) {
        if (l.getId() == null) {
            lessonMapper.insert(l);
        } else {
            lessonMapper.update(l);
        }
        return l.getId();
    }

    @Transactional
    public void removeLesson(Long id) {
        vocabMapper.deleteByLesson(id);
        lessonMapper.delete(id);
    }

    public Long saveVocabulary(Vocabulary v) {
        if (v.getId() == null) {
            vocabMapper.insert(v);
        } else {
            vocabMapper.update(v);
        }
        return v.getId();
    }

    public void removeVocabulary(Long id) {
        vocabMapper.delete(id);
    }

    public void reassign(Long vocabId, Long lessonId, Integer sortNo) {
        vocabMapper.reassign(vocabId, lessonId, sortNo == null ? 0 : sortNo);
        refreshWordCount(lessonId);
    }

    public void assignLessonBook(Long lessonId, Long bookId) {
        lessonMapper.assignBook(lessonId, bookId);
    }

    private void refreshWordCount(Long lessonId) {
        List<Vocabulary> vs = vocabMapper.findByLesson(lessonId);
        lessonMapper.updateWordCount(lessonId, vs.size());
    }

    /**
     * 批量导入：把解析后的词汇按 batchSize 切成若干课时，放入指定书本。
     * 幂等：先清空该书本下的旧课时和词汇，再重建。
     */
    @Transactional
    public ImportResult importVocabularies(int batchSize, Long bookId, List<Vocabulary> items) {
        if (batchSize < 1) batchSize = 50;
        if (bookId == null) {
            throw new IllegalArgumentException("必须指定目标书本");
        }

        // 清空该书本下的旧数据
        vocabMapper.deleteByBook(bookId);
        lessonMapper.deleteByBook(bookId);

        int total = items.size();
        if (total == 0) {
            ImportResult empty = new ImportResult();
            empty.setLessonCount(0);
            empty.setVocabCount(0);
            empty.setBookId(bookId);
            return empty;
        }

        int lessonCount = (int) Math.ceil((double) total / batchSize);
        int lessonNo = 1;
        for (int li = 0; li < lessonCount; li++) {
            int start = li * batchSize;
            int end = Math.min(start + batchSize, total);
            String first = items.get(start).getWord();
            String last = items.get(end - 1).getWord();

            Lesson lesson = new Lesson();
            lesson.setBookId(bookId);
            lesson.setTitle("Lesson " + lessonNo + "（" + first + " ~ " + last + "）");
            lesson.setSortNo(lessonNo);
            lesson.setDescription("自动导入生成，每课时约 " + batchSize + " 词");
            lesson.setWordCount(end - start);
            lesson.setStatus(1);
            lessonMapper.insert(lesson);

            for (int k = start; k < end; k++) {
                Vocabulary v = items.get(k);
                v.setLessonId(lesson.getId());
                v.setSortNo(k - start + 1);
                v.setStatus(1);
                vocabMapper.insert(v);
            }
            lessonNo++;
        }

        ImportResult r = new ImportResult();
        r.setLessonCount(lessonCount);
        r.setVocabCount(total);
        r.setBookId(bookId);
        return r;
    }

    // ===== 阅读书导入：把分段英文 + 中文翻译按课时存入 t_passage =====
    // 幂等：先清空该书本下的旧课时/词汇/篇章，再重建。
    @Transactional
    public ImportResult importReadingBook(Long bookId, List<ReadingSegment> segments) {
        if (bookId == null) throw new IllegalArgumentException("必须指定目标书本");
        Book book = bookMapper.findById(bookId);
        if (book == null) throw new BizException(404, "目标书本不存在");

        // 清空旧数据
        vocabMapper.deleteByBook(bookId);
        passageMapper.deleteByBook(bookId);
        lessonMapper.deleteByBook(bookId);

        int total = segments.size();
        if (total == 0) {
            ImportResult empty = new ImportResult();
            empty.setLessonCount(0);
            empty.setBookId(bookId);
            return empty;
        }

        int no = 1;
        for (ReadingSegment seg : segments) {
            Lesson lesson = new Lesson();
            lesson.setBookId(bookId);
            lesson.setTitle(seg.getTitle() != null ? seg.getTitle() : ("第 " + no + " 段"));
            lesson.setSortNo(no);
            lesson.setDescription("阅读片段");
            lesson.setWordCount(seg.getWords() != null ? seg.getWords() : null);
            lesson.setStatus(1);
            lessonMapper.insert(lesson);

            Passage passage = new Passage();
            passage.setLessonId(lesson.getId());
            passage.setSeq(1);
            passage.setEnText(seg.getEn());
            passage.setZhText(seg.getZh());
            passage.setStatus(1);
            passageMapper.insert(passage);
            no++;
        }

        ImportResult r = new ImportResult();
        r.setLessonCount(total);
        r.setBookId(bookId);
        return r;
    }

    // 取某课时的阅读篇章（学习需登录 + 目录可见）
    public Passage getPassage(Long lessonId, Long uid) {
        getLesson(lessonId, uid); // 校验可见性/登录
        List<Passage> list = passageMapper.findByLesson(lessonId);
        return list.isEmpty() ? null : list.get(0);
    }

    // 取某课时的篇章（管理后台用，不校验可见性）
    public Passage getPassageForAdmin(Long lessonId) {
        List<Passage> list = passageMapper.findByLesson(lessonId);
        return list.isEmpty() ? null : list.get(0);
    }

    // 保存/更新人工对齐结果
    @Transactional
    public void savePassageAlignment(Long lessonId, String alignmentJson) {
        Passage p = getPassageForAdmin(lessonId);
        if (p == null) throw new BizException(404, "该课时暂无阅读篇章");
        int flag = (alignmentJson != null && !alignmentJson.trim().isEmpty() && !"[]".equals(alignmentJson.trim())) ? 1 : 0;
        passageMapper.updateAlignment(p.getId(), alignmentJson, flag);
    }

    // ===== 用户级导入：上传 PDF 到自己的私有书（或新建） =====
    @Transactional
    public ImportResult importUserBook(Long uid, MultipartFile file, Long bookId, String bookTitle, Integer wordsPerLesson) {
        Book target;
        if (bookId != null) {
            target = bookMapper.findById(bookId);
            if (target == null) throw new BizException(404, "目标书本不存在");
            if (target.getUserId() == null || !target.getUserId().equals(uid)) {
                throw new BizException(403, "只能导入到自己的书本");
            }
            if (wordsPerLesson == null) {
                wordsPerLesson = target.getWordsPerLesson() != null ? target.getWordsPerLesson() : 50;
            }
        } else {
            if (bookTitle == null || bookTitle.trim().isEmpty()) {
                throw new BizException(400, "请填写书本名称");
            }
            target = new Book();
            target.setTitle(bookTitle.trim());
            target.setUserId(uid);
            target.setIsPublic(0);
            target.setWordsPerLesson(wordsPerLesson != null ? wordsPerLesson : 50);
            target.setStatus(1);
            target.setSortNo(999);
            bookMapper.insert(target);
        }
        List<Vocabulary> items = pdfParseService.parse(file);
        return importVocabularies(wordsPerLesson != null ? wordsPerLesson : 50, target.getId(), items);
    }

    // ===== 书本设置：每课词数 + 学习计划 =====
    public void updateBookPlan(Long bookId, Long uid, Integer wordsPerLesson,
                               Integer planDailyWords, Date planStartDate, Date planEndDate) {
        requireOwned(bookId, uid);
        bookMapper.updatePlan(bookId, wordsPerLesson, planDailyWords, planStartDate, planEndDate);
    }

    // ===== 重切分课时（按新每课词数重建，保留词汇） =====
    @Transactional
    public ImportResult resplitBook(Long bookId, Long uid, Integer wordsPerLesson) {
        Book b = requireOwned(bookId, uid);
        if (wordsPerLesson == null || wordsPerLesson < 1) {
            wordsPerLesson = b.getWordsPerLesson() != null ? b.getWordsPerLesson() : 50;
        }
        List<Vocabulary> items = vocabMapper.findByBook(bookId);
        if (items.isEmpty()) throw new BizException(400, "该书本暂无词汇，无法重切分");
        bookMapper.updatePlan(bookId, wordsPerLesson, b.getPlanDailyWords(), b.getPlanStartDate(), b.getPlanEndDate());
        return importVocabularies(wordsPerLesson, bookId, items);
    }

    // ===== 学习计划进度看板（目录级可见：登录用户与未登录均可查看进度概览） =====
    public BookPlan getBookPlan(Long bookId, Long uid) {
        Book b = bookMapper.findById(bookId);
        if (b == null || !isCatalogVisible(b, uid)) throw new BizException(404, "书本不存在");
        int total = lessonMapper.sumWordCountByBook(bookId);
        int learned = lessonMapper.countLearnedVocab(bookId, uid);
        int remain = Math.max(total - learned, 0);
        int percent = total > 0 ? (int) Math.round(learned * 100.0 / total) : 0;

        BookPlan plan = new BookPlan();
        plan.setBookId(bookId);
        plan.setTotalWords(total);
        plan.setLearnedWords(learned);
        plan.setRemainWords(remain);
        plan.setPercent(percent);
        plan.setDailyGoal(b.getPlanDailyWords());
        plan.setStartDate(b.getPlanStartDate());
        plan.setEndDate(b.getPlanEndDate());

        // 预计完成日 = 开始日 + ceil(剩余 / 每日目标) 天
        if (b.getPlanStartDate() != null && b.getPlanDailyWords() != null
                && b.getPlanDailyWords() > 0 && remain > 0) {
            LocalDate start = b.getPlanStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            long days = (long) Math.ceil((double) remain / b.getPlanDailyWords());
            plan.setExpectedFinishDate(Date.from(
                    start.plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        return plan;
    }

    public void removeUserBook(Long bookId, Long uid) {
        requireOwned(bookId, uid);
        bookMapper.delete(bookId);
    }

    // ===== 可见性辅助 =====
    private List<Long> visibleBookIds(Long uid) {
        if (isAdminUid(uid)) {
            return bookMapper.findAll().stream().map(Book::getId).collect(Collectors.toList());
        }
        return bookMapper.findAllVisible(uid).stream().map(Book::getId).collect(Collectors.toList());
    }

    // 目录级可见：公共书对所有人可见；管理员全部可见；展示书仅匿名目录预览；私有书仅所有者可见
    private boolean isCatalogVisible(Book b, Long uid) {
        if (b == null) return false;
        if (b.getIsPublic() != null && b.getIsPublic() == 1) return true;
        if (isAdminUid(uid)) return true;
        if (b.getBrowsePublic() != null && b.getBrowsePublic() == 1 && uid == null) return true;
        return uid != null && b.getUserId() != null && b.getUserId().equals(uid);
    }

    // 学习（课时/词汇）需登录，且书本对当前用户目录可见
    private void assertVisible(Long bookId, Long uid) {
        if (uid == null) throw new BizException(401, "请先登录后再学习");
        Book b = bookMapper.findById(bookId);
        if (b == null) throw new BizException(404, "书本不存在");
        if (!isCatalogVisible(b, uid)) {
            throw new BizException(403, "无权访问该书本");
        }
    }

    private Book requireOwned(Long bookId, Long uid) {
        Book b = bookMapper.findById(bookId);
        if (b == null) throw new BizException(404, "书本不存在");
        if (b.getUserId() == null || !b.getUserId().equals(uid)) {
            throw new BizException(403, "只能操作自己的书本");
        }
        return b;
    }

    @Data
    public static class ImportResult {
        private int lessonCount;
        private int vocabCount;
        private Long bookId;
    }
}
