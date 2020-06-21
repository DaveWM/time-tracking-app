describe('User management test', () => {
    beforeEach(() => {
        return cy.setup().then(() => cy.login());
    });

    it('Should be able to view all users', () => {
        cy.visit('/users')

       cy.get('.users-list__item').should($elems => expect($elems).to.have.length.of.greaterThan(1));       
    });

    it('should be able to add a user', () => {
        cy.visit('/users');
        cy.get('.users .uk-button-primary').contains('new user', {matchCase: false}).click();

        cy.url().should('contain', "/users/new");

        cy.get('#Email').type(`test${Math.floor(Math.random() * 1000)}@gmail.com`);
        cy.get('#Password').type('password123');

        cy.get('form').submit();

        cy.url().should('eq', Cypress.config().baseUrl + 'users');
    });

    it('should be able to edit a user', () => {
        cy.visit('/users');
        cy.get('.users-list__item .uk-button').contains('edit', {matchCase: false}).first().click();

        cy.get('#Email').type('a'); // add an extra character to the end of the email

        cy.get('form').submit();

        cy.url().should('eq', Cypress.config().baseUrl + 'users');
    });

    it('should be able to delete a user', () => {
        cy.visit('/users');

        cy.get('.users-list__item').its('length').then(numRows => {
            cy.get('.users-list__item').get('.uk-button-danger').first().click();

            cy.get('.users-list__item').should('have.length', numRows - 1);
        });
    });
})