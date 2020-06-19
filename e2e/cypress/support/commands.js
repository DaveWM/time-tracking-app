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
    const email = 'test@gmail.com';
    const pass = 'password123';

    cy.visit('/login');

    cy.get('#Email')
        .type(email);

    cy.get('#Password')
        .type(pass);

    cy.get('form').submit();

    return cy.url().should('eq', Cypress.config().baseUrl);
});
