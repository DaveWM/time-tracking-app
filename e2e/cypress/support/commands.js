// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************


Cypress.Commands.add("login", () => {
    const email = 'admin@gmail.com';
    const pass = 'password123';

    cy.visit('/login');

    cy.get('#Email')
        .type(email);

    cy.get('#Password')
        .type(pass);

    cy.get('form').submit();

    return cy.url().should('eq', Cypress.config().baseUrl);
});

Cypress.Commands.add("setup", () => {
    // hide re-frame-10x sidebar
    window.localStorage.setItem("day8.re-frame-10x.show-panel","\"false\"");
});