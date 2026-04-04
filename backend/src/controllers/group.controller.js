const crudController = require('./group.crud');
const memberController = require('./group.member');
const messageController = require('./group.message');

module.exports = {
  createGroup: crudController.createGroup,
  getGroups: crudController.getGroups,
  updateGroup: crudController.updateGroup,
  getInfoGroup: crudController.getInfoGroup,
  deleteGroup: crudController.deleteGroup,
  
  addMember: memberController.addMember,
  deleteMember: memberController.deleteMember,
  changeRole: memberController.changeRole,
  getMembers: memberController.getMembers,
  
  getGroupMessages: messageController.getGroupMessages
};