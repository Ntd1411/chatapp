const express = require("express");
const router = express.Router();
const authMiddleware = require("../middlewares/auth.middleware");

const controller = require("../controllers/group.controller");

router.post('/create', authMiddleware, controller.createGroup); // tạo nhóm mới
router.get('/getGroups', authMiddleware, controller.getGroups);  // lấy danh sách nhóm đã tham gia
router.delete('/delete/:id', authMiddleware, controller.deleteGroup); //rời nhóm hoặc xóa nhóm
router.patch('/update/:id', authMiddleware, controller.updateGroup);  // sửa thông tin nhóm
router.get('/:id', authMiddleware, controller.getInfoGroup); // xem thông tin nhóm
router.get('/:id/messages', authMiddleware, controller.getGroupMessages); //lấy tin nhắn trong nhóm đó
router.post('/:id/addMembers', authMiddleware, controller.addMember); //thêm thành viên nhóm
router.get('/:id/getMembers', authMiddleware, controller.getMembers); //lấy danh sách thành viên
router.delete('/:id/deleteMembers/:memberId', authMiddleware, controller.deleteMember);  //xóa thành viên nhóm
router.patch('/:id/changeRole/:memberId', authMiddleware, controller.changeRole); // đổi role thành viên

module.exports = router;