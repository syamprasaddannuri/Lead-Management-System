import { Lead } from './api'

// A ready email for each pipeline stage. {firstName} and {program} are filled from the lead.
export const STAGE_EMAIL_TEMPLATES: Record<string, { label: string; subject: string; body: string }> = {
  NEW: {
    label: 'New — welcome / intro',
    subject: 'Welcome to Lead Management System, {firstName}',
    body: 'Hi {firstName},\n\nThanks for your interest in {program} at Lead Management System. I would love to walk you through how the program works and help you get started.\n\nWhen is a good time for a quick call?\n\nBest,\nLead Management System Team',
  },
  CONTACTED: {
    label: 'Contacted — follow up',
    subject: 'Following up on {program}',
    body: 'Hi {firstName},\n\nI tried reaching you about {program} but could not connect. I would still love to help you explore whether it is the right fit.\n\nCould you share a convenient time to talk?\n\nBest,\nLead Management System Team',
  },
  QUALIFIED: {
    label: 'Qualified — next steps',
    subject: 'Next steps for {program}',
    body: 'Hi {firstName},\n\nGreat speaking with you. Here are the next steps to join {program}, along with the curriculum and what to expect.\n\nHappy to answer any questions.\n\nBest,\nLead Management System Team',
  },
  NURTURING: {
    label: 'Nurturing — keep in touch',
    subject: 'Keeping you in the loop on {program}',
    body: 'Hi {firstName},\n\nSharing a few resources on {program} while you decide. We are here whenever you are ready to move forward.\n\nBest,\nLead Management System Team',
  },
  NEGOTIATION: {
    label: 'Negotiation — enrollment offer',
    subject: 'Your enrollment details for {program}',
    body: 'Hi {firstName},\n\nAs discussed, here are your enrollment details and options for {program}. Let me know if you would like to proceed and I will help with the next steps.\n\nBest,\nLead Management System Team',
  },
  WON: {
    label: 'Won — welcome aboard',
    subject: 'Welcome aboard, {firstName}!',
    body: 'Hi {firstName},\n\nWelcome to {program}! We are excited to have you. Your onboarding details will follow shortly.\n\nBest,\nLead Management System Team',
  },
  LOST: {
    label: 'Lost — stay in touch',
    subject: 'Staying in touch',
    body: 'Hi {firstName},\n\nThanks for considering {program}. If anything changes or you would like to revisit it later, we would be glad to help.\n\nWishing you the best,\nLead Management System Team',
  },
}

export function fillTemplate(text: string, lead: Lead): string {
  const name = lead.firstName?.trim() || 'there'
  const program = lead.courseName?.trim() || 'our program'
  return text.replace(/\{firstName\}/g, name).replace(/\{program\}/g, program)
}
