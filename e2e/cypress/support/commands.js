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
    const password = 'password123';

    return cy.request('POST', 'http://localhost:8081/login', {email, password})
        .then(response => {
            window.localStorage.setItem('auth-token', response.body.token);
        });
});

Cypress.Commands.add("setup", () => {
    // hide re-frame-10x sidebar
    window.localStorage.setItem("day8.re-frame-10x.show-panel","\"false\"");
});